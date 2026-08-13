package app.velora

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.vk.id.VKID

@HiltAndroidApp
class VeloraApplication : Application(),ImageLoaderFactory {
    @Inject lateinit var imageLoader:ImageLoader
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.VK_CONFIGURED) VKID.init(this)
    }
    override fun newImageLoader()=imageLoader
}
