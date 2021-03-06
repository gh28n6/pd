package pd.fenc;

import static pd.fenc.IReader.EOF;

public class ScalarPicker extends NumberPicker {

    public static String pickDottedIdentifier(CharReader src) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (!pickIdentifier(src, IWriter.unicodeStream(sb))) {
                throw new ParsingException();
            }
            if (!src.hasNext() || src.next() != '.') {
                return sb.toString();
            }
            sb.append('.');
        }
    }

    public static String pickIdentifier(CharReader src) {
        StringBuilder sb = new StringBuilder();
        if (!pickIdentifier(src, IWriter.unicodeStream(sb))) {
            throw new ParsingException();
        }
        return sb.toString();
    }

    /**
     * identifier matches [a-zA-Z_][a-zA-Z_0-9]*<br/>
     * if fail, src.next() will be the illegal character
     */
    private static boolean pickIdentifier(CharReader src, IWriter dst) {
        int stat = 0;
        while (true) {
            int ch = src.hasNext() ? src.next() : EOF;
            switch (stat) {
                case 0:
                    if (Cascii.isAlpha(ch) || ch == '_') {
                        dst.push(ch);
                        stat = 1;
                    } else {
                        src.moveBack();
                        return false;
                    }
                    break;
                case 1:
                    if (Cascii.isAlpha(ch) || ch == '_' || Cascii.isDigit(ch)) {
                        dst.push(ch);
                    } else {
                        src.moveBack();
                        return true;
                    }
                    break;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    public static String pickString(CharReader src) {
        return pickString(src, EOF);
    }

    public static String pickString(CharReader src, int closingSymbol) {
        StringBuilder sb = new StringBuilder();
        if (!pickString(src, closingSymbol, IWriter.unicodeStream(sb))) {
            throw new ParsingException();
        }
        return sb.toString();
    }

    /**
     * Will succ in front of `closingSymbol`<br/>
     * - `closingSymbol` will not be consumed and not be a part of result<br/>
     * - `closingSymbol` can be escaped by `\`<br/>
     * - `closingSymbol` can be `EOF`<br/>
     * Will fail in front of `EOF`<br/>
     */
    public static boolean pickString(CharReader src, int closingSymbol, IWriter dst) {
        boolean isEscaped = false;
        while (true) {
            int ch = src.hasNext() ? src.next() : EOF;
            if (isEscaped) {
                isEscaped = false;
                dst.push('\\');
                dst.push(ch);
            } else if (ch == '\\') {
                isEscaped = true;
            } else if (ch == closingSymbol) {
                if (closingSymbol != EOF) {
                    src.moveBack();
                }
                return true;
            } else if (ch == EOF) {
                return false;
            } else {
                dst.push(ch);
            }
        }
    }

    private ScalarPicker() {
        // dummy
    }
}
