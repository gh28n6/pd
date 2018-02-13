package libcliff.io.codec;

import java.util.Arrays;

import libcliff.io.BytePullStream;
import libcliff.io.BytePushStream;
import libcliff.io.Pullable;
import libcliff.io.Pushable;

/**
 * byte[3] => byte[4]
 */
public class Base64 implements BytePullStream, BytePushStream {

    private class Puller implements BytePullStream {

        private int[] base64 = null;

        private int[] parsed = new int[3];

        private int pIndex = 3;

        private boolean ends = false;

        private Pullable upstream = null;

        private void fromBase64Bytes(int[] base64, int[] dst) {
            dst[0] = (base64[0] << 2) | (base64[1] >> 4);
            dst[1] = (base64[1] << 4) | (base64[2] >> 2);
            dst[2] = (base64[2] << 6) | base64[3];
        }

        private int getBase64Bytes(Pullable pullable, int[] base64) {
            assert base64 != null && base64.length >= 4;
            for (int i = 0; i < 4; ++i) {
                int j = pullable.pull();
                if (j == -1) {
                    return i;
                }
                base64[i] = DECODE_MAP[j & 0xFF];
            }
            return 4;
        }

        @Override
        public Puller join(Pullable upstream) {
            this.upstream = upstream;
            return this;
        }

        @Override
        public int pull() {
            if (ends) {
                return -1;
            }
            if (base64 == null) {
                // the first time
                base64 = new int[4];
                getBase64Bytes(upstream, base64);
            }
            if (pIndex == 3) {
                fromBase64Bytes(base64, parsed);
                getBase64Bytes(upstream, base64);
                if (base64[0] == '=') {
                    parsed[0] = -1;
                    if (base64[1] == '=') {
                        parsed[1] = -1;
                    }
                }
                pIndex = 0;
            }
            int aByte = parsed[pIndex++];
            if (aByte == -1) {
                ends = true;
            }
            return aByte;
        }
    }

    private class Pusher implements BytePushStream {

        private int[] parsed = new int[3];

        private int pIndex = 3;

        private boolean ends = false;

        private Pushable downstream = null;

        public int flush() {
            ends = true;
            return toBase64Bytes(parsed, 0, pIndex, downstream);
        }

        @Override
        public Pusher join(Pushable downstream) {
            this.downstream = downstream;
            return this;
        }

        @Override
        public int push(int aByte) {
            if (ends) {
                return -1;
            }
            parsed[pIndex++] = aByte & 0xFF;
            if (pIndex == 3) {
                pIndex = 0;
                return toBase64Bytes(parsed, 0, 3, downstream);
            }
            return 0;
        }

        private int toBase64Bytes(int byte0, int byte1, int byte2,
                Pushable pushable) {
            int size = 0;
            size += pushable.push(
                    ENCODE_MAP[(byte0 & 0xFF) >>> 2]);
            size += pushable.push(
                    ENCODE_MAP[((byte0 & 0x03) << 4) | ((byte1 & 0xFF) >>> 4)]);
            size += pushable.push(
                    ENCODE_MAP[((byte1 & 0x0F) << 2) | ((byte2 & 0xFF) >>> 6)]);
            size += pushable.push(
                    ENCODE_MAP[byte2 & 0x3F]);
            return size;
        }

        private int toBase64Bytes(int[] a, int i, int j,
                Pushable pushable) {
            switch (j - i) {
                case 0:
                    return 0;
                case 1: {
                    int size = 0;
                    size += toBase64Bytes(a[i], 0, 0, pushable);
                    size += pushable.push('=');
                    size += pushable.push('=');
                    return size;
                }
                case 2: {
                    int size = 0;
                    size += toBase64Bytes(a[i], a[i + 1], 0, pushable);
                    size += pushable.push('=');
                    return size;
                }
                case 3:
                    return toBase64Bytes(a[i], a[i + 1], a[i + 2], pushable);
                default:
                    throw new ParsingException();
            }
        }
    }

    private static final char[] ENCODE_MAP;

    private static final char[] DECODE_MAP;

    static {
        ENCODE_MAP = new char[] {
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
                'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
                'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd',
                'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
                'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
                'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7',
                '8', '9', '+', '/'
        };

        DECODE_MAP = new char[127];
        Arrays.fill(DECODE_MAP, (char) -1);
        for (char i = 0; i < ENCODE_MAP.length; ++i) {
            DECODE_MAP[ENCODE_MAP[i]] = i;
        }
    }

    private Puller puller = null;

    private Pusher pusher = null;

    private Base64() {
        puller = new Puller();
        pusher = new Pusher();
    }

    @Override
    public Puller join(Pullable upstream) {
        return puller.join(upstream);
    }

    @Override
    public Pusher join(Pushable downstream) {
        return pusher.join(downstream);
    }

    @Override
    public int pull() {
        return puller.pull();
    }

    @Override
    public int push(int ch) {
        if (ch >= 0) {
            return pusher.push(ch);
        } else if (ch == -1) {
            return pusher.flush();
        }
        throw new ParsingException();
    }
}