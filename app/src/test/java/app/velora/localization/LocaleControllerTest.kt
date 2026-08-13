package app.velora.localization

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import app.velora.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LocaleControllerTest {
    @After fun reset() = AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())

    @Test fun `manual language choice is represented by persistent app locale state`() {
        val activity = Robolectric.buildActivity(LocaleTestActivity::class.java).setup().get()
        LocaleController.apply(AppLanguage.Russian)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AppLanguage.Russian, LocaleController.current())
        assertEquals("ru", AppCompatDelegate.getApplicationLocales().get(0)?.language)
        activity.recreate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AppLanguage.Russian, LocaleController.current())
        assertEquals("ru", AppCompatDelegate.getApplicationLocales().get(0)?.language)
        activity.finish()
    }

    @Test fun `russian and english resources are complete and selectable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("С возвращением", localized(context, "ru").getString(R.string.login_title))
        assertEquals("Welcome back", localized(context, "en").getString(R.string.login_title))
        assertEquals("ПОДКЛЮЧЁННЫЕ АККАУНТЫ", localized(context, "ru").getString(R.string.connected_accounts))
    }

    private fun localized(context: Context, language: String): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration)
    }
}

class LocaleTestActivity : AppCompatActivity()
