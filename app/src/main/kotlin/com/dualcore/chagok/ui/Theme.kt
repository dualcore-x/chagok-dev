package com.dualcore.chagok.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.dualcore.chagok.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 차량의 주간·야간 설정을 따른다. 낮에는 밝은 바탕, 밤에는 눈부심을 줄이는 남색 바탕.
 * 강조색은 파랑 하나, "중요"는 노란 톤으로 따로 둬서 강조색과 섞이지 않게 한다.
 */
private val day = lightColorScheme(
    primary = Color(0xFF2F6BEF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0B2A6B),
    secondaryContainer = Color(0xFFE8ECF3),
    onSecondaryContainer = Color(0xFF26303F),
    tertiaryContainer = Color(0xFFFFE7B3),
    onTertiaryContainer = Color(0xFF4A3300),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF151A23),
    surface = Color(0xFFF4F6FA),
    onSurface = Color(0xFF151A23),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF5A6474),
    surfaceContainerHigh = Color(0xFFEEF3FF),
    outline = Color(0xFFC2CAD6),
    outlineVariant = Color(0xFFDDE2EA),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val night = darkColorScheme(
    primary = Color(0xFF6EA8FF),
    onPrimary = Color(0xFF00214D),
    primaryContainer = Color(0xFF1E3D6E),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondaryContainer = Color(0xFF27324A),
    onSecondaryContainer = Color(0xFFDDE4F2),
    tertiaryContainer = Color(0xFF5C4512),
    onTertiaryContainer = Color(0xFFFFE3A6),
    background = Color(0xFF0D1320),
    onBackground = Color(0xFFE6EBF5),
    surface = Color(0xFF0D1320),
    onSurface = Color(0xFFE6EBF5),
    surfaceVariant = Color(0xFF172033),
    onSurfaceVariant = Color(0xFFAEB9CC),
    surfaceContainerHigh = Color(0xFF1D2940),
    outline = Color(0xFF4D5A73),
    outlineVariant = Color(0xFF2E3A52),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5F1A16),
    onErrorContainer = Color(0xFFFFDAD6),
)

// Pretendard Std(OFL-1.1, assets/licenses). 자주 쓰는 한글 2,350자와 영문만 담은 경량판이라 드문 글자는 시스템 글꼴로 보인다.
private val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

// 글자는 휴대폰보다 한 단계 크게 쓴다. 모든 글자에 Pretendard를 쓰고 자간을 살짝 좁혀 단정하게 보이게 한다.
private val base = Typography()
private fun TextStyle.pretendard() = copy(fontFamily = Pretendard, letterSpacing = (-0.2).sp)
private val typography = Typography(
    displayLarge = base.displayLarge.pretendard(),
    displayMedium = base.displayMedium.pretendard(),
    displaySmall = base.displaySmall.pretendard(),
    headlineLarge = base.headlineLarge.pretendard(),
    headlineSmall = base.headlineSmall.pretendard(),
    titleSmall = base.titleSmall.pretendard(),
    headlineMedium = base.headlineMedium.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold).pretendard(),
    titleLarge = base.titleLarge.copy(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold).pretendard(),
    titleMedium = base.titleMedium.copy(fontSize = 21.sp, lineHeight = 28.sp).pretendard(),
    bodyLarge = base.bodyLarge.copy(fontSize = 20.sp, lineHeight = 30.sp).pretendard(),
    bodyMedium = base.bodyMedium.copy(fontSize = 19.sp, lineHeight = 27.sp).pretendard(),
    labelLarge = base.labelLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold).pretendard(),
    labelMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium).pretendard(),
    // 입력칸 글자 수(0/300) 같은 가장 작은 글자도 차 안에서 읽히게 키운다.
    bodySmall = base.bodySmall.copy(fontSize = 16.sp, lineHeight = 22.sp).pretendard(),
    labelSmall = base.labelSmall.copy(fontSize = 15.sp, lineHeight = 20.sp).pretendard(),
)

// 모서리는 넉넉히 둥글게. 카드와 버튼이 부드럽고 친근해 보이게 한다.
private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

/** 앱 화면에 쓰는 배율 밀도. 안내창은 별도 창이라 시스템 밀도로 돌아가므로 이 값을 다시 씌운다(AppAlertDialog). */
val LocalAppDensity = staticCompositionLocalOf<Density?> { null }

/** 화면 비율을 맞추는 기준 폭. 이보다 넓은 창에서는 전체를 비례해서 키운다. */
private const val DesignWidthDp = 1280f

/**
 * Pleos처럼 큰 화면(2560×1440, 160dpi, 앱 영역 약 1680dp)에서는 dp 크기 그대로면 글자와 버튼이 화면에 비해 작다.
 * 앱 창 폭이 기준보다 넓으면 밀도를 올려 글자·버튼·여백을 같은 비율로 키운다(최대 1.5배). 좁은 화면은 그대로다.
 */
@Composable
fun DriveCompanionTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = (LocalConfiguration.current.screenWidthDp / DesignWidthDp).coerceIn(1f, 1.5f)
    val scaled = Density(density.density * scale, density.fontScale)
    CompositionLocalProvider(LocalDensity provides scaled, LocalAppDensity provides scaled) {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) night else day, typography = typography, shapes = shapes, content = content)
    }
}
