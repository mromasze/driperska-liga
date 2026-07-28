# Draft audio

Drop audio files here and the draft board picks them up with no code change. Every file is optional:
when one is missing, `src/lib/sound.ts` synthesises a short WebAudio tone in its place, so the draft
always has audible feedback even on a fresh checkout.

Volume and mute are controlled by the speaker + slider in the draft header and persist per browser
(`driperska-draft-volume` / `driperska-draft-muted` in localStorage). Browsers block audio until the
first real interaction, so nothing plays until the player clicks anywhere on the page.

| File                     | When it plays                                  |
| ------------------------ | ---------------------------------------------- |
| `draft-music.mp3` (or `.ogg`) | Looping bed for the whole draft. **Put the League soundtrack here.** |
| `draft-start.mp3`        | Draft opens                                    |
| `your-turn.mp3`          | It becomes your ban/pick                       |
| `lock-in.mp3`            | A pick is locked in                            |
| `ban.mp3`                | A champion is banned                           |
| `tick.mp3`               | Each of the last 5 seconds of a step           |
| `draft-done.mp3`         | Draft finishes                                 |
| `error.mp3`              | An action was rejected                         |

Notes:

- Keep the music loopable and reasonably small — it is served as a static asset, not streamed.
- The container CSP has no `media-src` of its own, so it falls back to `default-src 'self'`: audio must
  be served from this origin (i.e. from this folder), not from a CDN.
- Only ship music you have the right to distribute. Riot's
  [Legal Jibber Jabber](https://www.riotgames.com/en/legal) covers fan use of League assets.
