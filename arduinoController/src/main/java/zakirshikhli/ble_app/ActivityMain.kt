package zakirshikhli.ble_app

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import zakirshikhli.ble_app.R.string.*
import java.util.Locale


@Suppress("DEPRECATION")
class ActivityMain : AppCompatActivity(), FragmentManager.OnBackStackChangedListener {

    companion object {
        var btIsClassic = false
        lateinit var prefs: SharedPreferences
        var lang: String = ""


        fun Context.setAppLocale(string: String): Context {
            val locale = Locale(string)
            Locale.setDefault(locale)
            val config = resources.configuration
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return createConfigurationContext(config)
        }

    }

    private lateinit var mainMenuContainer: View
    private lateinit var helpPanel: View
    private var fragmentObject: Fragment? = null

    override fun attachBaseContext(newBase: Context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(newBase)
        lang = prefs.getString("lang", "en").toString()
        super.attachBaseContext(ContextWrapper(newBase.setAppLocale(lang))) // Set app locale
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setBackgroundColor(resources.getColor(R.color.backgroundDark))
        toolbar.setTitleTextAppearance(this, R.style.CustomTextStyle)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // Disable Toolbar
        val appBarLayout = findViewById<View>(R.id.appBarLayout)

        supportFragmentManager.addOnBackStackChangedListener(this)


        val info: PackageInfo = this.packageManager.getPackageInfo(this.packageName, 0)
        val appInfo = findViewById<TextView>(R.id.app_info)
        appInfo.text = getString(ver_info) + info.versionName

        mainMenuContainer = findViewById(R.id.mainMenuContainer)
        val buttonClassic = findViewById<View>(R.id.buttonClassic)
        val buttonBLE = findViewById<View>(R.id.buttonBLE)

        helpPanel = findViewById(R.id.helpPanel)
        helpPanel.visibility = View.GONE

        val settingsButton: ImageButton = findViewById(R.id.lang_button)
        settingsButton.setOnClickListener {
            var l = 0
            when (lang) {
                "az" -> l = 0
                "en" -> l = 1
                "ru" -> l = 2
            }

            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle(resources.getString(lang_title))
                .setPositiveButton(getString(confirm)) { _, _ ->
                    this.finish()
                    startActivity(intent)
                }.setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }.setSingleChoiceItems(
                    arrayOf(getString(aze), getString(eng), getString(rus)), l
                ) { _, which ->
                    when (which) {
                        0 -> lang = "az"
                        1 -> lang = "en"
                        2 -> lang = "ru"
                    }
                    prefs.edit().putString("lang", lang).apply()
                }

            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
        val helpButton: ImageButton = findViewById(R.id.help_button)
        helpButton.setOnClickListener {
            helpPanel.visibility = View.VISIBLE
        }
        val helpCloseButton: AppCompatButton = findViewById(R.id.helpCloseButton)
        helpCloseButton.setOnClickListener {
            helpPanel.visibility = View.GONE
        }

        val shareButton: ImageButton = findViewById(R.id.shareButton)
        shareButton.setOnClickListener {
            @Suppress("DEPRECATION") ShareCompat.IntentBuilder.from(this)
                .setText(getString(share_message)).setSubject(getString(app_name))
                .setType("text/plain").setChooserTitle(getString(share_via)).startChooser()
        }

        val emailButton: ImageButton = findViewById(R.id.emailButton)
        emailButton.setOnClickListener {
            val uri: Uri = Uri.parse("mailto:zakirshikhli@gmail.com?subject=${getString(app_name)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

        val quitButton: ImageButton = findViewById(R.id.quitButton)
        quitButton.setOnClickListener {
            finish()
        }

        fragmentObject = supportFragmentManager.findFragmentById(R.id.fragment)
        if (fragmentObject != null) {
            mainMenuContainer.visibility = View.GONE
        } else {
            mainMenuContainer.visibility = View.VISIBLE
        }

        buttonBLE.setOnClickListener {
            mainMenuContainer.visibility = View.GONE
            btIsClassic = false
            appBarLayout.visibility = View.VISIBLE

            supportFragmentManager.beginTransaction()
                .add(R.id.fragment, FragmentDevicesBLE(), "devicesBLE").addToBackStack(null)
                .commit()
        }

        buttonClassic.setOnClickListener {
            mainMenuContainer.visibility = View.GONE
            btIsClassic = true
            appBarLayout.visibility = View.GONE

            Toast.makeText(this, waitText, Toast.LENGTH_LONG).show()
            Handler().postDelayed({
                supportFragmentManager.beginTransaction()
                    .add(R.id.fragment, FragmentDevicesClassic(), "devicesClassic").addToBackStack(null)
                    .commit()
            }, 100)
        }


    }





    override fun onBackStackChanged() {
        try {
            supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
        } catch (e: Exception) {
            Log.e("TAG", e.toString())
        }

    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private var doubleBackToExitPressedOnce = false

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (helpPanel.visibility == View.VISIBLE) {
            helpPanel.visibility = View.GONE
            return
        }


        val fragmentDevicesBLE: Fragment? = supportFragmentManager.findFragmentByTag("devicesBLE")
        val fragmentClassicClassic: Fragment? = supportFragmentManager.findFragmentByTag("devicesClassic")
        val fragmentController: Fragment? = supportFragmentManager.findFragmentByTag("controller")

        if ((fragmentDevicesBLE != null && fragmentDevicesBLE.isVisible) ||
            (fragmentClassicClassic != null && fragmentClassicClassic.isVisible) ||
            (fragmentController != null && fragmentController.isVisible)) {

            val intent = this.intent
            this.finish()
            startActivity(intent)
            return
        }


        /////////////////////////
        // Double Back exit stuff
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed()
            return
        }
        this.doubleBackToExitPressedOnce = true
        Toast.makeText(this, getString(quitToast), Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
        // Double Back exit stuff
        /////////////////////////
    }

}
