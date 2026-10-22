package __PACKAGE__

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private var taps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply { text = "__APP_NAME__"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        val count = TextView(this).apply { text = "0"; textSize = 48f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        val button = Button(this).apply { text = "Tap me"; setOnClickListener { count.text = (++taps).toString() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.parseColor("#101418")); setPadding(48, 48, 48, 48)
            addView(title); addView(count); addView(button)
        })
    }
}
