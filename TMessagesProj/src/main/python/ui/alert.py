"""Telegram-style alert dialogs backed by the BYgram plugin host."""
from client_utils import _context, _invoke, get_hook_account
import json


class AlertDialogBuilder:
    ALERT_TYPE_MESSAGE = 0
    ALERT_TYPE_LOADING = 2
    ALERT_TYPE_SPINNER = 3
    BUTTON_POSITIVE = -1
    BUTTON_NEGATIVE = -2
    BUTTON_NEUTRAL = -3
    _BACK = -4
    _CANCEL = -5
    _DISMISS = -6

    def __init__(self, context=None, progress_style=0, resources_provider=None):
        self._context, self._account = _context(), get_hook_account()
        self._android_context = context
        self._resources = resources_provider
        self._spec = {"style": progress_style}
        self._buttons = {}
        self._events = {}
        self._view = None
        self._top_drawable = None
        self._handle = None
        self._progress = 0

    def set_title(self, title):
        self._spec["title"] = title
        return self

    def set_message(self, message):
        self._spec["message"] = message
        return self

    def set_cancelable(self, value):
        self._spec["cancelable"] = bool(value)
        self._update_cancellation()
        return self

    def set_canceled_on_touch_outside(self, cancel):
        self._spec["outside"] = bool(cancel)
        self._update_cancellation()
        return self

    def _update_cancellation(self):
        if self._handle is not None:
            self._handle.setCancellation(self._spec.get("cancelable", True), self._spec.get("outside", True))

    def set_view(self, view, height=-2):
        self._view = view
        self._spec["viewHeight"] = height
        return self

    def set_items(self, items, listener=None, icons=None):
        items = list(items)
        if icons is not None:
            icons = list(icons)
            if len(icons) != len(items):
                raise ValueError("Provide one icon per alert item")
        self._spec["items"] = items
        self._spec.pop("icons", None)
        if icons is not None:
            self._spec["icons"] = icons
        self._items_listener = listener
        return self

    def set_message_text_view_clickable(self, clickable):
        self._spec["messageClickable"] = bool(clickable)
        return self

    def set_dim_enabled(self, enabled):
        self._spec["dim"] = bool(enabled)
        return self

    def set_blurred_background(self, blur, blur_behind_if_possible=True):
        self._spec.update(blur=bool(blur), blurBehind=bool(blur and blur_behind_if_possible))
        return self

    def set_top_image(self, res_id, background_color):
        self._top_drawable = None
        self._spec.pop("topAnimation", None)
        self._spec.update(topImage=res_id, topColor=background_color)
        return self

    def set_top_drawable(self, drawable, background_color):
        self._top_drawable = drawable
        self._spec.pop("topAnimation", None)
        self._spec.pop("topImage", None)
        self._spec["topColor"] = background_color
        return self

    def set_top_animation(self, res_id, size, auto_repeat, background_color, layer_colors=None):
        self._top_drawable = None
        self._spec.update(topAnimation=res_id, topSize=size, topRepeat=auto_repeat,
                          topColor=background_color, layerColors=layer_colors or {})
        return self

    def make_button_red(self, button_type):
        self._spec.setdefault("redButtons", []).append(button_type)
        return self

    def set_dialog_button_color_key(self, theme_key):
        self._spec["buttonColorKey"] = theme_key
        return self

    def set_on_back_button_listener(self, listener=None):
        self._events[self._BACK] = listener
        return self

    def set_on_cancel_listener(self, listener=None):
        self._events[self._CANCEL] = listener
        return self

    def set_on_dismiss_listener(self, listener=None):
        self._events[self._DISMISS] = listener
        return self

    def _set_button(self, which, name, text, listener):
        self._spec[name] = text
        self._buttons[which] = listener
        return self

    def set_positive_button(self, text, listener=None):
        return self._set_button(self.BUTTON_POSITIVE, "positive", text, listener)

    def set_negative_button(self, text, listener=None):
        return self._set_button(self.BUTTON_NEGATIVE, "negative", text, listener)

    def set_neutral_button(self, text, listener=None):
        return self._set_button(self.BUTTON_NEUTRAL, "neutral", text, listener)

    def create(self):
        if self._handle is None:
            self._create(False)
        return self

    def show(self):
        if self._handle is None:
            self._create(True)
        else:
            self._handle.show()
        return self

    def _create(self, show):

        def click(which):
            if which == self._DISMISS:
                self._handle = None
            if which in self._events:
                callback = self._events[which]
                if callback is not None:
                    args = (self, self.BUTTON_NEGATIVE) if which == self._BACK else (self,)
                    _invoke(self._context, self._account, callback, *args)
                return
            callback = self._buttons.get(which) if which < 0 else getattr(self, "_items_listener", None)
            if callback is not None:
                _invoke(self._context, self._account, callback, self, which)

        self._handle = self._context._bridge.pluginAlert(json.dumps(self._spec, ensure_ascii=False), self._view, click,
                                                        self._android_context, self._resources, self._top_drawable, show)
        if self._progress:
            self._handle.setProgress(self._progress)

    def get_dialog(self):
        return None if self._handle is None else self._handle.getDialog()

    def get_button(self, button_type):
        dialog = self.get_dialog()
        return None if dialog is None else dialog.getButton(button_type)

    def set_progress(self, value):
        self._progress = value
        if self._handle is not None:
            self._handle.setProgress(value)

    def dismiss(self):
        if self._handle is not None:
            handle, self._handle = self._handle, None
            handle.close()
