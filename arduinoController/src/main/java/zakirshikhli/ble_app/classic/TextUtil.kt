package zakirshikhli.ble_app.classic

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import androidx.annotation.ColorInt

internal object TextUtil {
    @ColorInt
    var caretBackground: Int = -0x99999a

    const val newline_crlf: String = "\r\n"
    const val newline_lf: String = "\n"


    @JvmOverloads
    fun toCaretString(s: CharSequence, keepNewline: Boolean, length: Int = s.length): CharSequence {
        var found = false
        for (pos in 0 until length) {
            if (s[pos].code < 32 && (!keepNewline || s[pos] != '\n')) {
                found = true
                break
            }
        }
        if (!found) return s
        val sb = SpannableStringBuilder()
        for (pos in 0 until length) if (s[pos].code < 32 && (!keepNewline || s[pos] != '\n')) {
            sb.append('^')
            sb.append((s[pos].code + 64).toChar())
            sb.setSpan(
                BackgroundColorSpan(caretBackground),
                sb.length - 2,
                sb.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else {
            sb.append(s[pos])
        }
        return sb
    }
}
