package com.nuvio.app.features.livetv

import androidx.compose.runtime.Composable
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_no_guide_data
import org.jetbrains.compose.resources.stringResource

/** Small composable string accessors so grid cells can stay free of resource imports. */
internal object LiveTvStrings {
    @Composable
    fun noGuideData(): String = stringResource(Res.string.live_tv_no_guide_data)
}
