import java.util.Arrays;

import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;

/** Checks that history's TL media snapshots retain old attachments and InstantPage content. */
public class BygramEditMediaCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static byte[] encode(TLRPC.MessageMedia media) {
        SerializedData data = new SerializedData(media.getObjectSize());
        try {
            media.serializeToStream(data);
            return data.toByteArray();
        } finally {
            data.cleanup();
        }
    }

    private static TLRPC.MessageMedia decode(byte[] bytes) {
        SerializedData data = new SerializedData(bytes);
        try {
            TLRPC.MessageMedia result = TLRPC.MessageMedia.TLdeserialize(data, data.readInt32(true), true);
            check(data.remaining() == 0, "incomplete media snapshot");
            return result;
        } finally {
            data.cleanup();
        }
    }

    public static void main(String[] args) {
        TLRPC.TL_messageMediaPhoto photo = new TLRPC.TL_messageMediaPhoto();
        photo.flags = 1;
        photo.photo = new TLRPC.TL_photoEmpty();
        photo.photo.id = 100;
        byte[] previousPhoto = encode(photo);
        photo.photo.id = 101;
        check(!Arrays.equals(previousPhoto, encode(photo)), "photo-only edit was missed");
        check(decode(previousPhoto).photo.id == 100, "previous photo lost");

        TLRPC.TL_messageMediaDocument document = new TLRPC.TL_messageMediaDocument();
        document.flags = 1;
        document.document = new TLRPC.TL_documentEmpty();
        document.document.id = 200;
        byte[] previousDocument = encode(document);
        document.document.id = 201;
        check(!Arrays.equals(previousDocument, encode(document)), "document-only edit was missed");
        check(decode(previousDocument).document.id == 200, "previous document lost");

        TLRPC.TL_messageMediaWebPage media = new TLRPC.TL_messageMediaWebPage();
        TLRPC.TL_webPage page = new TLRPC.TL_webPage();
        media.webpage = page;
        page.flags = 1 << 10;
        page.id = 300;
        page.url = page.display_url = "https://example.org/article";
        page.cached_page = new TL_iv.TL_page();
        page.cached_page.url = page.url;
        TL_iv.pageBlockParagraph paragraph = new TL_iv.pageBlockParagraph();
        TL_iv.textPlain text = new TL_iv.textPlain();
        text.text = "Previous InstantPage";
        paragraph.text = text;
        page.cached_page.blocks.add(paragraph);
        page.cached_page.photos.add(photo.photo);
        page.cached_page.documents.add(document.document);
        byte[] previousPage = encode(media);
        text.text = "Edited InstantPage";
        check(!Arrays.equals(previousPage, encode(media)), "InstantPage-only edit was missed");
        TLRPC.WebPage restored = decode(previousPage).webpage;
        check(restored.cached_page != null, "InstantPage lost");
        check("Previous InstantPage".equals(((TL_iv.textPlain) restored.cached_page.blocks.get(0).text).text), "old page text lost");
        check(restored.cached_page.photos.get(0).id == 101, "page photos lost");
        check(restored.cached_page.documents.get(0).id == 201, "page documents lost");
        check(Arrays.equals(previousPage, encode(decode(previousPage))), "snapshot changed after reload");
        check(decode(encode(new TLRPC.TL_messageMediaEmpty())) instanceof TLRPC.TL_messageMediaEmpty, "removed media lost");
        System.out.println("PASS: photo/document replacement, InstantPage text and attachments, snapshot reload");
    }
}
