package pl.romcio.driperska.common.settings;

/** How a runtime setting is edited and rendered in the admin panel. */
public enum SettingType {
    /** Free text. */
    STRING,
    /** Free text that is never sent back to the browser in full — only a masked preview. */
    SECRET,
    /** {@code true} / {@code false}. */
    BOOLEAN,
    /** Whole number. */
    INTEGER,
    /** Free text with a suggested set of values (rendered as a select + "own value"). */
    CHOICE
}
