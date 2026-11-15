import com.android.build.api.dsl.LibraryExtension
import com.skypass.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            // :core:designsystem -> com.skypass.core.designsystem
            namespace = "com.skypass" + path.replace(':', '.')
        }
    }
}
