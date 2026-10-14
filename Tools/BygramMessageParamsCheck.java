import java.lang.reflect.Constructor;

import org.telegram.messenger.MessageCustomParamsHelper;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/** Runs the actual local-params serializer without loading Android JNI. */
public class BygramMessageParamsCheck {
    private static Constructor<?> constructor;

    private static TLObject params(TLRPC.Message message) throws Exception {
        return (TLObject) constructor.newInstance(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        constructor = Class.forName("org.telegram.messenger.MessageCustomParamsHelper$Params_v1")
                .getDeclaredConstructor(TLRPC.Message.class);
        constructor.setAccessible(true);
        // BYgram 12.10.5 v1 fixture: bit 14, three voice booleans, voice ID, premium boolean.
        SerializedData legacy = new SerializedData();
        legacy.writeInt32(1);
        legacy.writeInt32(1 << 14);
        for (int i = 0; i < 3; i++) legacy.writeBool(false);
        legacy.writeInt64(0);
        legacy.writeBool(false);
        SerializedData legacyInput = new SerializedData(legacy.toByteArray());
        legacyInput.readInt32(true);
        TLRPC.Message legacyMessage = new TLRPC.TL_message();
        params(legacyMessage).readParams(legacyInput, true);
        check(legacyMessage.bygramDeleted, "12.10.5 deleted-message fixture lost its Spy flag");
        legacyInput.cleanup();
        legacy.cleanup();
        for (boolean deleted : new boolean[]{false, true}) {
            for (boolean preparing : new boolean[]{false, true}) {
                TLRPC.Message source = new TLRPC.TL_message();
                source.bygramDeleted = deleted;
                TLRPC.TL_messageActionGramTransfer action = new TLRPC.TL_messageActionGramTransfer();
                source.action = action;
                action.comment_encrypted_preparing = preparing;
                check(MessageCustomParamsHelper.isEmpty(source) == (!deleted && !preparing), "empty params");
                SerializedData output = new SerializedData();
                params(source).serializeToStream(output);
                byte[] bytes = output.toByteArray();
                SerializedData input = new SerializedData(bytes);
                check(input.readInt32(true) == 1, "legacy params version changed");
                check(input.readInt32(true) == ((deleted ? 1 << 14 : 0) | (preparing ? 1 << 15 : 0)),
                        "Spy and wallet flags overlap or legacy Spy bit moved");
                input.cleanup();
                for (boolean encrypted : new boolean[]{false, true}) {
                    TLRPC.Message restored = new TLRPC.TL_message();
                    TLRPC.TL_messageActionGramTransfer restoredAction = new TLRPC.TL_messageActionGramTransfer();
                    restored.action = restoredAction;
                    restoredAction.comment_encrypted = encrypted;
                    input = new SerializedData(bytes);
                    input.readInt32(true);
                    params(restored).readParams(input, true);
                    check(restored.bygramDeleted == deleted, "Spy state lost after reload");
                    check(restoredAction.comment_encrypted_preparing == (preparing && !encrypted),
                            "wallet encryption state corrupted");
                    input.cleanup();
                }
                TLRPC.Message copy = new TLRPC.TL_message();
                MessageCustomParamsHelper.copyParams(source, copy);
                check(copy.bygramDeleted == deleted, "Spy state lost when copying params");
                output.cleanup();
            }
        }
        System.out.println("PASS: legacy Spy bit, independent wallet state, reload and copy");
    }
}
