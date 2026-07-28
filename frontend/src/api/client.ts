import { useAuthStore } from '../store/auth';
import type { ProblemDetail, RefreshResponse } from './types';

/** Base URL — relative so it works behind the nginx reverse proxy (docs/08). */
export const API_BASE = '/api/v1';

export class ApiError extends Error {
  readonly status: number;
  readonly problem?: ProblemDetail;

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

type QueryValue = string | number | boolean | undefined | null;

export interface RequestOptions {
  method?: string;
  /** JSON body — serialised automatically. Use `formData` for multipart. */
  body?: unknown;
  formData?: FormData;
  query?: Record<string, QueryValue>;
  signal?: AbortSignal;
  /** Skip Authorization header + refresh flow (used by auth endpoints). */
  skipAuth?: boolean;
}

function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = `${API_BASE}${path}`;
  if (!query) return url;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params.append(key, String(value));
    }
  }
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

async function parseError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail | undefined;
  try {
    const data = (await response.json()) as ProblemDetail;
    if (data && typeof data === 'object') problem = data;
  } catch {
    // non-JSON error body — ignore, fall back to status text
  }
  const message = problem?.detail ?? problem?.title ?? response.statusText ?? 'Request failed';
  return new ApiError(response.status, message, problem);
}

// --- Single-flight token refresh (docs/06 §6.7) --------------------------
let refreshInFlight: Promise<boolean> | null = null;

async function refreshAccessToken(): Promise<boolean> {
  const { refreshToken } = useAuthStore.getState();
  if (refreshToken) {
    const response = await fetch(buildUrl('/auth/refresh'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (response.ok) {
      const data = (await response.json()) as RefreshResponse;
      useAuthStore.getState().setAccessToken(data.accessToken, data.refreshToken);
      return true;
    }
  }
  // The refresh token is gone or rejected (it is scoped to the backend's boot id, so a restart kills
  // it). With "remember me" on we can mint a whole new session instead of interrupting the user.
  if (await reloginFromStoredCredentials()) return true;
  useAuthStore.getState().clear();
  return false;
}

/**
 * Logs in again from remembered credentials. Written against `fetch` rather than the `api` helper on
 * purpose: this runs *inside* the refresh path, and going back through `request` would re-enter the
 * 401 interceptor.
 */
async function reloginFromStoredCredentials(): Promise<boolean> {
  const { remember, credentials } = useAuthStore.getState();
  if (!remember || !credentials) return false;
  try {
    const response = await fetch(buildUrl('/auth/login'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: credentials.username, password: credentials.password }),
    });
    if (!response.ok) {
      // Password changed, account disabled, or a Turnstile challenge only a human can pass.
      useAuthStore.getState().forgetCredentials();
      return false;
    }
    useAuthStore.getState().setAuth((await response.json()) as RefreshResponse);
    return true;
  } catch {
    // Network failure: keep the credentials, the ServerGate will retry once the backend is back.
    return false;
  }
}

function ensureRefresh(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = refreshAccessToken().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function doFetch(url: string, options: RequestOptions): Promise<Response> {
  const headers = new Headers();
  let payload: BodyInit | undefined;

  if (options.formData) {
    payload = options.formData; // browser sets multipart boundary
  } else if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
    payload = JSON.stringify(options.body);
  }

  if (!options.skipAuth) {
    const token = useAuthStore.getState().accessToken;
    if (token) headers.set('Authorization', `Bearer ${token}`);
  }

  return fetch(url, {
    method: options.method ?? 'GET',
    headers,
    body: payload,
    signal: options.signal,
  });
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = buildUrl(path, options.query);

  let response = await doFetch(url, options);

  // 401 interceptor: refresh the access token once, then retry the request.
  if (response.status === 401 && !options.skipAuth) {
    const refreshed = await ensureRefresh();
    if (refreshed) {
      response = await doFetch(url, options);
    } else {
      useAuthStore.getState().clear();
    }
  }

  if (!response.ok) {
    throw await parseError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get('Content-Type') ?? '';
  if (!contentType.includes('application/json')) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method'>) =>
    request<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method'>) =>
    request<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method'>) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'DELETE' }),
  upload: <T>(path: string, formData: FormData, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'POST', formData }),
};
