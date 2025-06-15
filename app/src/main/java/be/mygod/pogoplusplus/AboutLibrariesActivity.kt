package be.mygod.pogoplusplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import be.mygod.pogoplusplus.App.Companion.app
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.util.withContext

class AboutLibrariesActivity : ComponentActivity(), UriHandler {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(if (isSystemInDarkTheme()) darkColorScheme(
                primary = Color(colorResource(id = R.color.primaryLightColor).value),
                secondary = Color(colorResource(id = R.color.secondaryLightColor).value),
            ) else lightColorScheme(
                primary = Color(colorResource(id = R.color.primaryDarkColor).value),
                secondary = Color(colorResource(id = R.color.secondaryDarkColor).value),
            )) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(stringResource(com.google.android.gms.oss.licenses.R.string.oss_license_title))
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ),
                        )
                    }
                ) { contentPadding ->
                    CompositionLocalProvider(LocalUriHandler provides this) {
                        LibrariesContainer(
                            Libs.Builder().withContext(this).build(),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = contentPadding,
                        )
                    }
                }
            }
        }
    }
    override fun openUri(uri: String) = app.launchUrl(this, uri)
}
