"""Exercise the production Spy archive SQL with an isolated SQLite database."""
import re
import sqlite3
from pathlib import Path

source = (Path(__file__).resolve().parents[1] /
          "TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java").read_text(encoding="utf-8")


def literals(fragment):
    return "".join(re.findall(r'"([^"]*)"', fragment))


def statement(prefix, end):
    return literals('"' + prefix + source.split('"' + prefix, 1)[1].split(end, 1)[0])


columns = re.search(r'BYGRAM_SPY_COLUMNS =\s*"([^"]+)"', source).group(1)
# The trigger's body contains its own semicolon inside the Java string.
trigger_body = source.split("String capture =", 1)[1].split('END";', 1)[0] + 'END"'
trigger_body = literals(trigger_body.replace('" + BYGRAM_SPY_COLUMNS + "', columns)
                       .replace('" + values + "', "NEW." + columns.replace(", ", ", NEW.")))
schema = statement("CREATE TABLE messages_v2", ").stepThis()")
archive = statement("CREATE TABLE IF NOT EXISTS bygram_spy_messages", ").stepThis()")
flag_query = statement("SELECT EXISTS (SELECT 1 FROM bygram_spy_messages", ", did, did, did)")
batch_query = statement("SELECT uid, mid, is_channel FROM bygram_spy_messages", ", afterDid, afterDid, afterMid)")
restore = (f"INSERT OR IGNORE INTO messages_v2 ({columns}) SELECT {columns} "
           "FROM bygram_spy_messages WHERE saved = 1 AND uid = ?")

with sqlite3.connect(":memory:") as db:
    db.execute(schema)
    db.execute(archive)
    db.execute("CREATE TEMP TABLE bygram_spy_capture(enabled INTEGER)")
    db.execute("CREATE TEMP TRIGGER spy_insert AFTER INSERT" + trigger_body)
    db.execute("CREATE TEMP TRIGGER spy_update AFTER UPDATE OF data, mid, send_state" + trigger_body)
    db.execute("INSERT INTO messages_v2 (uid, mid, data, send_state) VALUES (8, 1, X'01', 0)")
    assert db.execute("SELECT count(*) FROM bygram_spy_messages").fetchone() == (0,)

    db.execute("INSERT INTO bygram_spy_capture VALUES (1)")
    db.executemany("INSERT INTO messages_v2 (uid, mid, data, send_state, is_channel) VALUES (?, ?, ?, ?, ?)", [
        (8, 2, b"incoming", 0, 0), (8, 3, b"still live", 0, 0),
        (-81, 2, b"channel", 0, 1), (8, -4, b"outgoing", 1, 0),
    ])
    db.execute("UPDATE messages_v2 SET mid = 4, send_state = 0 WHERE uid = 8 AND mid = -4")
    assert db.execute("SELECT data FROM bygram_spy_messages WHERE uid = 8 AND mid = 4").fetchone() == (b"outgoing",)

    db.execute("UPDATE bygram_spy_messages SET pending = 1 WHERE uid = 8")
    db.execute("DELETE FROM messages_v2 WHERE uid = 8")
    db.execute("INSERT INTO messages_v2 (uid, mid, data, send_state) VALUES (8, 3, X'FF', 0)")
    assert db.execute(batch_query, (-(2**63), -(2**63), 0)).fetchall() == [(8, 2, 0), (8, 4, 0)]

    db.execute("UPDATE bygram_spy_messages SET saved = 1, pending = 0 WHERE uid = 8 AND mid = 2")
    db.execute(restore, (8,))
    assert db.execute("SELECT data FROM messages_v2 WHERE uid = 8 AND mid = 2").fetchone() == (b"incoming",)
    assert db.execute(flag_query, (8, 8, 8)).fetchone() == (0,), "Mixed history must not show a trash icon"
    db.execute("DELETE FROM bygram_spy_messages WHERE uid = 8 AND saved = 0")
    assert db.execute(flag_query, (8, 8, 8)).fetchone() == (0,), "A live cache row must block the trash icon"
    db.execute("DELETE FROM messages_v2 WHERE uid = 8 AND mid = 3")
    assert db.execute(flag_query, (8, 8, 8)).fetchone() == (1,), "Saved-only history must show a trash icon"
    db.execute("DELETE FROM bygram_spy_messages WHERE uid = 8 AND mid = 2")
    db.execute("DELETE FROM messages_v2 WHERE uid = 8 AND mid = 2")
    db.execute(restore, (8,))
    assert db.execute(flag_query, (8, 8, 8)).fetchone() == (0,), "A purged chat must stay gone"
    assert db.execute("SELECT data FROM bygram_spy_messages WHERE uid = -81 AND mid = 2").fetchone() == (b"channel",)

print("PASS: archive capture, sent IDs, resync, scoped restore, trash marker, purge, and channel ID collisions")
