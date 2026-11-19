"""Build Telegram text and entities with UTF-16 offsets."""
from enum import Enum
from dataclasses import dataclass
from html.parser import HTMLParser
from html import escape
import re


class TLEntityType(str, Enum):
    BOLD = "Bold"
    ITALIC = "Italic"
    UNDERLINE = "Underline"
    STRIKETHROUGH = "Strike"
    CODE = "Code"
    PRE = "Pre"
    TEXT_LINK = "TextUrl"
    SPOILER = "Spoiler"
    BLOCKQUOTE = "Blockquote"
    CUSTOM_EMOJI = "CustomEmoji"


@dataclass
class RawEntity:
    type: TLEntityType
    offset: int
    length: int
    url: str | None = None
    language: str | None = None
    document_id: int | None = None
    collapsed: bool = False


_TAGS = {"b": "Bold", "strong": "Bold", "i": "Italic", "em": "Italic",
         "u": "Underline", "ins": "Underline", "s": "Strike", "strike": "Strike", "del": "Strike",
         "code": "Code", "pre": "Pre", "a": "TextUrl", "spoiler": "Spoiler", "tg-spoiler": "Spoiler",
         "blockquote": "Blockquote", "emoji": "CustomEmoji", "tg-emoji": "CustomEmoji"}


class _HTML(HTMLParser):
    def __init__(self):
        super().__init__()
        self.text, self.entities, self.stack = [], [], []
        self.text_offset = 0

    def handle_data(self, data):
        self.text.append(data)
        self.text_offset += len(data.encode("utf-16-le")) // 2

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "br":
            self.handle_data("\n")
        elif tag == "code" and self.stack and self.stack[-1][0] == "pre":
            language = attrs.get("class") or ""
            if language.startswith("language-"):
                self.stack[-1][2]["language"] = language[9:]
        elif tag in _TAGS:
            self.stack.append((tag, self.text_offset, attrs))

    def handle_endtag(self, tag):
        if self.stack and self.stack[-1][0] == tag:
            self._close(*self.stack.pop())

    def _close(self, tag, offset, attrs):
        if offset == self.text_offset:
            return
        values = {}
        kind = _TAGS[tag]
        if kind == "TextUrl":
            if not attrs.get("href"):
                return
            values["url"] = attrs["href"]
        elif kind == "CustomEmoji":
            value = attrs.get("id") or attrs.get("emoji-id") or ""
            if not re.fullmatch(r"-?[0-9]{1,19}", value) or not -(2**63) <= int(value) < 2**63:
                return
            values["document_id"] = int(value)
        elif kind == "Pre":
            values["language"] = attrs.get("language") or ""
        elif kind == "Blockquote":
            values["collapsed"] = "expandable" in attrs or "collapsed" in attrs
        self.entities.append((kind, offset, self.text_offset - offset, values))


_MD_TAGS = {"**": "b", "__": "u", "~~": "s", "~": "s", "||": "spoiler", "*": "b", "_": "i", "`": "code"}
_TOKEN = re.compile(r"\\(.)|```([^\n`]*)\n([\s\S]+?)```|(!?)\[([^\]\n]+)\]\(([^)\n]+)\)|(\*\*|__|~~|~|\|\||\*|_|\x60)(.+?)\7|^(\*\*)?> ?([^\n]*)", re.DOTALL | re.MULTILINE)


def _markdown(text, depth=0):
    if depth >= 24:
        return escape(text)
    output, end = [], 0
    for match in _TOKEN.finditer(text):
        output.append(escape(text[end:match.start()]))
        escaped, language, code, emoji, label, url, marker, body, collapsed, quote = match.groups()
        if escaped:
            output.append(escape(escaped))
        elif code is not None:
            output.append(f'<pre language="{escape(language)}">{escape(code)}</pre>')
        elif label is not None:
            if emoji and url.startswith("tg://emoji?id="):
                output.append(f'<emoji id="{escape(url[14:])}">{escape(label)}</emoji>')
            else:
                output.append(f'<a href="{escape(url)}">{_markdown(label, depth + 1)}</a>')
        elif quote is not None:
            attribute = " expandable" if collapsed else ""
            output.append(f"<blockquote{attribute}>{_markdown(quote, depth + 1)}</blockquote>")
        else:
            tag = _MD_TAGS[marker]
            content = escape(body) if tag == "code" else _markdown(body, depth + 1)
            output.append(f"<{tag}>{content}</{tag}>")
        end = match.end()
    output.append(escape(text[end:]))
    return "".join(output)


def parse_text(text, parse_mode="HTML", is_caption=False):
    key = "caption" if is_caption else "message"
    if parse_mode is None:
        return {key: text, "entities": []}
    mode = parse_mode.lower()
    if mode not in ("html", "markdown"):
        raise ValueError("Unsupported parse mode")
    parser = _HTML()
    parser.feed(_markdown(text) if mode == "markdown" else text)
    parser.close()
    while parser.stack:
        parser._close(*parser.stack.pop())
    from java import jclass
    entities = []
    for kind, offset, length, values in sorted(parser.entities, key=lambda spec: (spec[1], -spec[2])):
        entity = jclass("org.telegram.tgnet.TLRPC$TL_messageEntity" + kind)()
        entity.offset, entity.length = offset, length
        for name, value in values.items():
            setattr(entity, name, value)
        entities.append(entity)
    return {key: "".join(parser.text), "entities": entities}
