"""ADB check for settings added in the final BYgram batch."""
import sys
import time

from check_bygram_preferences import adb, back, capture, checked, click, find, locate, open_root, snapshot


def require(*labels):
    for label in labels:
        _, node = locate(label)
        assert node is not None, f"Missing new setting: {label}"


def top():
    for _ in range(10):
        adb("shell", "input", "swipe", "600", "800", "600", "2200", "400")
        time.sleep(0.3)


if "--resume-navigation" not in sys.argv:
    open_root()
    click("Внешний вид")
    top()
    original_md3 = checked("Переключатели Material 3")
    try:
        click("Переключатели Material 3")
        assert checked("Переключатели Material 3") != original_md3
        capture("new-md3-switches")
    finally:
        if checked("Переключатели Material 3") != original_md3:
            click("Переключатели Material 3")

    top()
    click("Папки с чатами")
    require("Оформление вкладок папок", "Скрыть счётчики непрочитанных", "Скрыть вкладку «Все чаты»", "Управление папками")
    capture("new-folders")
    back()
    click("Навигация приложения")
require("Нижняя навигация", "Набор иконок", "Радиус Pill Stack", "Контур стеклянной навигации", "Предпочитать размытие стеклянных поверхностей")
capture("new-navigation")
back()
back()

click("Чаты")
top()
click("Жесты")
require("Двойное нажатие на входящие", "Двойное нажатие на исходящие", "Свайп входящих сообщений влево", "Свайп исходящих сообщений влево")
capture("new-gestures")
back()
click("Меню сообщения")
require("Группировать действия сообщений", "Скрыть пункты меню")
capture("new-message-menu")
back()
require("Широкие публикации в каналах", "Широкие публикации в ленте",
        "Действия администратора вверху меню сообщения", "Быстрые переходы в чатах и темах",
        "Вычислять выражения, заканчивающиеся знаком =", "Камера для видеосообщений", "Камера для кружков",
        "Шаг перемотки двойным нажатием", "Предпочитать исходное качество видео", "Пауза видеосообщений при сворачивании")
capture("new-chat-features")
back()
print("PASS: final-batch MD3, folders, navigation, gestures, message menu, posts, calculator, camera and video settings")
