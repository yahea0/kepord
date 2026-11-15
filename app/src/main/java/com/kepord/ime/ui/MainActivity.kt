package com.kepord.ime.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kepord.ime.KepordApplication
import com.kepord.ime.clipboard.ClipEntry
import com.kepord.ime.clipboard.ClipboardStore
import com.kepord.ime.data.KepordPreferences
import com.kepord.ime.ime.ArabicFontEngine
import com.kepord.ime.ime.ArabicFontStyle
import com.kepord.ime.model.Background3DStyle
import com.kepord.ime.model.KepordConfig
import com.kepord.ime.model.LetterColorMode
import com.kepord.ime.model.WavePattern
import com.kepord.ime.ui.components.LiveKeyboardEnginePreview
import com.kepord.ime.ui.theme.KepordPalette
import com.kepord.ime.ui.theme.KepordTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.BLACK
        window.navigationBarColor = AndroidColor.BLACK
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val app = applicationContext as KepordApplication

        setContent {
            KepordTheme {
                KepordMainScreen(
                    preferences = app.preferences,
                    clipboardStore = app.clipboardStore
                )
            }
        }
    }
}

private enum class KepordTab(val title: String, val icon: ImageVector) {
    HOME_TEST("التجربة", Icons.Rounded.Keyboard),
    LETTER_RGB("الأحرف", Icons.Rounded.Palette),
    THREE_D("3D والخلفية", Icons.Rounded.ViewInAr),
    FONTS_SYSTEM("الخط والنظام", Icons.Rounded.Tune),
    CLIPBOARD("الحافظة", Icons.Rounded.ContentPaste)
}

@Composable
private fun KepordMainScreen(
    preferences: KepordPreferences,
    clipboardStore: ClipboardStore
) {
    val context = LocalContext.current
    val config by preferences.config.collectAsState()
    val clips by clipboardStore.entries.collectAsState()
    var activeTab by remember { mutableIntStateOf(0) }

    var isImeEnabled by remember { mutableStateOf(false) }
    var isImeSelected by remember { mutableStateOf(false) }

    fun refreshImeStatus() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledList = imm.enabledInputMethodList
        isImeEnabled = enabledList.any { it.packageName == context.packageName }
        val defaultIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        isImeSelected = defaultIme?.startsWith(context.packageName) == true
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardStore.captureFromSystemClipboard(cm)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshImeStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) { refreshImeStatus() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(KepordPalette.PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        containerColor = KepordPalette.PureBlack,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KepordPalette.PureBlack)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "KEPORD PRO",
                            color = KepordPalette.PureWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.8.sp
                        )
                        Text(
                            text = "لوحة المفاتيح الميكانيكية 3D & الحافظة اللانهائية",
                            color = KepordPalette.MutedGray,
                            fontSize = 11.5.sp
                        )
                    }
                    Surface(
                        color = KepordPalette.SurfaceElevated,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (isImeSelected) KepordPalette.PureWhite else KepordPalette.BorderStrong)
                    ) {
                        Text(
                            text = if (isImeSelected) "مفعّل ونشط" else if (isImeEnabled) "بانتظار الاختيار" else "غير مفعّل",
                            color = KepordPalette.PureWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = KepordPalette.BorderHairline, thickness = 1.dp)
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = KepordPalette.SurfaceCard,
                tonalElevation = 0.dp
            ) {
                KepordTab.entries.forEachIndexed { idx, tab ->
                    val selected = activeTab == idx
                    NavigationBarItem(
                        selected = selected,
                        onClick = { activeTab = idx },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = KepordPalette.PureBlack,
                            selectedTextColor = KepordPalette.PureWhite,
                            indicatorColor = KepordPalette.PureWhite,
                            unselectedIconColor = KepordPalette.MutedGray,
                            unselectedTextColor = KepordPalette.MutedGray
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(KepordPalette.PureBlack)
        ) {
            when (KepordTab.entries[activeTab]) {
                KepordTab.HOME_TEST -> HomeAndLiveTestTab(
                    config = config,
                    clipsCount = clips.size,
                    isImeEnabled = isImeEnabled,
                    isImeSelected = isImeSelected,
                    onOpenImeSettings = {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onShowImePicker = {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showInputMethodPicker()
                    },
                    onGoToFonts = { activeTab = KepordTab.FONTS_SYSTEM.ordinal },
                    onCopyToClipboard = { txt ->
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("kepord_test", txt))
                        clipboardStore.saveTextAsync(txt)
                        Toast.makeText(context, "تم النسخ إلى الحافظة الدائمة بنجاح", Toast.LENGTH_SHORT).show()
                    }
                )

                KepordTab.LETTER_RGB -> LetterLightingTab(
                    config = config,
                    onUpdate = { preferences.update(it) }
                )

                KepordTab.THREE_D -> ThreeDBackgroundTab(
                    config = config,
                    onUpdate = { preferences.update(it) }
                )

                KepordTab.FONTS_SYSTEM -> FontsAndSystemTab(
                    config = config,
                    onUpdate = { preferences.update(it) },
                    onResetDefaults = {
                        preferences.resetDefaults()
                        Toast.makeText(context, "تمت استعادة الإعدادات الافتراضية", Toast.LENGTH_SHORT).show()
                    }
                )

                KepordTab.CLIPBOARD -> ClipboardVaultTab(
                    config = config,
                    clips = clips,
                    clipboardStore = clipboardStore,
                    onUpdateConfig = { transform ->
                        preferences.update(transform)
                        clipboardStore.enforceLimitAsync()
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeAndLiveTestTab(
    config: KepordConfig,
    clipsCount: Int,
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    onOpenImeSettings: () -> Unit,
    onShowImePicker: () -> Unit,
    onGoToFonts: () -> Unit,
    onCopyToClipboard: (String) -> Unit
) {
    var testText by remember { mutableStateOf("") }
    var previewArabic by remember { mutableStateOf(config.defaultLanguageArabic) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LuxuryCard(title = "حالة التفعيل على النظام (Android 13)") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isImeSelected) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = KepordPalette.PureWhite
                )
                Text(
                    text = when {
                        isImeSelected -> "كيبورد Kepord محدد ونشط حالياً. كل تعديل تطبّقه يظهر فوراً أثناء التجربة."
                        isImeEnabled -> "التطبيق مفعّل في النظام، اضغط (اختيار الكيبورد) وحدد Kepord Pro 3D."
                        else -> "الخطوة 1: فعّل Kepord من إعدادات اللغة والإدخال، ثم الخطوة 2: اختره كلوحة مفاتيح نشطة."
                    },
                    color = KepordPalette.SoftWhite,
                    fontSize = 12.5.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenImeSettings,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, KepordPalette.PureWhite),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KepordPalette.PureWhite)
                ) {
                    Text("1. تفعيل بالنظام", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onShowImePicker,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KepordPalette.PureWhite,
                        contentColor = KepordPalette.PureBlack
                    )
                ) {
                    Text("2. اختيار Kepord", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LuxuryCard(title = "استوديو التجربة المباشرة (Live Test Box)") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGoToFonts),
                shape = RoundedCornerShape(10.dp),
                color = KepordPalette.SurfaceElevated,
                border = BorderStroke(1.dp, KepordPalette.BorderStrong)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("الخط العربي النشط للكتابة:", color = KepordPalette.MutedGray, fontSize = 10.5.sp)
                        Text(config.arabicFontStyle.titleAr, color = KepordPalette.PureWhite, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("تغيير الخط ›", color = KepordPalette.PureWhite, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                value = testText,
                onValueChange = { testText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 260.dp),
                placeholder = {
                    Text(
                        "اضغط هنا واكتب أو الصق لتجربة الإضاءة 3D والخطوط والإيموجي فوراً...",
                        color = KepordPalette.MutedGray
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KepordPalette.PureWhite,
                    unfocusedBorderColor = KepordPalette.BorderStrong,
                    focusedTextColor = KepordPalette.PureWhite,
                    unfocusedTextColor = KepordPalette.PureWhite,
                    cursorColor = KepordPalette.PureWhite,
                    focusedContainerColor = KepordPalette.PureBlack,
                    unfocusedContainerColor = KepordPalette.PureBlack
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lines = if (testText.isEmpty()) 0 else testText.count { it == '\n' } + 1
                Text(
                    text = "الأحرف: ${testText.length}  |  الأسطر: $lines",
                    color = KepordPalette.MutedGray,
                    fontSize = 11.5.sp
                )
                if (testText.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { testText = "" },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, KepordPalette.BorderStrong),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KepordPalette.PureWhite)
                    ) {
                        Text("مسح حقل التجربة", fontSize = 11.sp)
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val sample = "مرحباً بك في كيبورد Kepord Pro 3D!\nاختبار العربية مع التشكيل: أَبْجَدْ هَوَّزْ حُطِّي كَلَمُنْ — English Test 12345"
                        onCopyToClipboard(sample)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, KepordPalette.BorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KepordPalette.PureWhite)
                ) {
                    Text("نسخ نص تجريبي للحافظة", fontSize = 11.5.sp)
                }

                OutlinedButton(
                    onClick = {
                        val sb = StringBuilder(95000)
                        for (i in 1..2000) {
                            sb.append("Line ").append(i).append(": val keycap_").append(i)
                                .append(" = Kepord3DEngine.render(depth = 4, glow = true) // اختبار التحمل الكامل للأسطر\n")
                        }
                        onCopyToClipboard(sb.toString())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, KepordPalette.PureWhite),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KepordPalette.PureWhite)
                ) {
                    Text("نسخ 2000 سطر برمجي لاختبار الحافظة", fontSize = 11.5.sp)
                }
            }
        }

        LuxuryCard(title = "معاينة حية لمحرك الرسم 3D الآن") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${config.letterColorMode.titleAr} • ${config.background3DStyle.titleAr}",
                    color = KepordPalette.MutedGray,
                    fontSize = 11.5.sp
                )
                OutlinedButton(
                    onClick = { previewArabic = !previewArabic },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    border = BorderStroke(1.dp, KepordPalette.BorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KepordPalette.PureWhite)
                ) {
                    Text(if (previewArabic) "معاينة: عربي" else "معاينة: EN", fontSize = 11.sp)
                }
            }
            LiveKeyboardEnginePreview(config = config, arabicPreview = previewArabic)
            Text(
                text = "عناصر الحافظة المحفوظة الآن: $clipsCount عنصر",
                color = KepordPalette.MutedGray,
                fontSize = 11.5.sp
            )
        }
    }
}

@Composable
private fun LetterLightingTab(
    config: KepordConfig,
    onUpdate: ((KepordConfig) -> KepordConfig) -> Unit
) {
    var editingStopIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LiveKeyboardEnginePreview(config = config, arabicPreview = config.defaultLanguageArabic)

        LuxuryCard(title = "نمط إضاءة الأحرف نفسها (وليس محيطها)") {
            LetterColorMode.entries.forEach { mode ->
                val selected = config.letterColorMode == mode
                SelectableRow(
                    title = mode.titleAr,
                    subtitle = mode.descAr,
                    selected = selected,
                    onClick = { onUpdate { it.copy(letterColorMode = mode) } }
                )
            }
        }

        if (config.letterColorMode == LetterColorMode.STATIC ||
            config.letterColorMode == LetterColorMode.CUSTOM_GRADIENT_WAVE
        ) {
            LuxuryCard(
                title = if (config.letterColorMode == LetterColorMode.STATIC)
                    "اختيار لون الحرف الثابت"
                else
                    "تخصيص ألوان التدرج الثلاثي"
            ) {
                if (config.letterColorMode == LetterColorMode.CUSTOM_GRADIENT_WAVE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val stops = listOf(
                            1 to config.gradientColor1,
                            2 to config.gradientColor2,
                            3 to config.gradientColor3
                        )
                        stops.forEach { (idx, col) ->
                            val isEditing = (editingStopIndex == idx) || (editingStopIndex == 0 && idx == 1)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { editingStopIndex = idx },
                                shape = RoundedCornerShape(10.dp),
                                color = KepordPalette.SurfaceElevated,
                                border = BorderStroke(
                                    if (isEditing) 2.dp else 1.dp,
                                    if (isEditing) KepordPalette.PureWhite else KepordPalette.BorderHairline
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(col))
                                            .border(1.dp, Color.White, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("لون $idx", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                val targetColor = when {
                    config.letterColorMode == LetterColorMode.STATIC -> config.staticLetterColor
                    editingStopIndex == 2 -> config.gradientColor2
                    editingStopIndex == 3 -> config.gradientColor3
                    else -> config.gradientColor1
                }

                val updateTargetColor: (Int) -> Unit = { newCol ->
                    onUpdate { c ->
                        when {
                            c.letterColorMode == LetterColorMode.STATIC -> c.copy(staticLetterColor = newCol)
                            editingStopIndex == 2 -> c.copy(gradientColor2 = newCol)
                            editingStopIndex == 3 -> c.copy(gradientColor3 = newCol)
                            else -> c.copy(gradientColor1 = newCol)
                        }
                    }
                }

                ColorPickerControls(selectedColor = targetColor, onColorChange = updateTargetColor)
            }
        }

        if (config.letterColorMode != LetterColorMode.STATIC) {
            LuxuryCard(title = "شكل التموج وسرعته") {
                WavePattern.entries.forEach { wp ->
                    SelectableRow(
                        title = wp.titleAr,
                        subtitle = null,
                        selected = config.wavePattern == wp,
                        onClick = { onUpdate { it.copy(wavePattern = wp) } }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                LuxurySlider(
                    label = "سرعة التموج",
                    valueText = String.format("%.1fx", config.waveSpeed),
                    value = config.waveSpeed,
                    range = 0.1f..4.0f,
                    onValueChange = { v -> onUpdate { it.copy(waveSpeed = v) } }
                )
            }
        }

        LuxuryCard(title = "توهج الحرف وتفاعل اللمس") {
            LuxurySlider(
                label = "قوة توهج إضاءة الحرف",
                valueText = "${(config.letterGlow * 100).roundToInt()}%",
                value = config.letterGlow,
                range = 0f..1f,
                onValueChange = { v -> onUpdate { it.copy(letterGlow = v) } }
            )
            LuxurySwitchRow(
                title = "موجة تفاعلية عند لمس أي حرف",
                subtitle = "نبضة ضوئية تنتشر على الأحرف المجاورة فور الضغط",
                checked = config.reactiveTouchRipple,
                onCheckedChange = { b -> onUpdate { it.copy(reactiveTouchRipple = b) } }
            )
        }
    }
}

@Composable
private fun ColorPickerControls(
    selectedColor: Int,
    onColorChange: (Int) -> Unit
) {
    val presets = listOf(
        0xFFFFFFFF.toInt(),
        0xFF00F0FF.toInt(),
        0xFF00E676.toInt(),
        0xFFFFEA00.toInt(),
        0xFFFF5252.toInt(),
        0xFFFF007F.toInt(),
        0xFFAA00FF.toInt(),
        0xFF448AFF.toInt()
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        presets.forEach { preset ->
            val active = preset == selectedColor
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(preset))
                    .border(
                        width = if (active) 3.dp else 1.dp,
                        color = if (active) Color.White else Color.Gray,
                        shape = CircleShape
                    )
                    .clickable { onColorChange(preset) }
            )
        }
    }

    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(selectedColor, hsv)

    LuxurySlider(
        label = "درجة اللون (Hue)",
        valueText = "${hsv[0].roundToInt()}°",
        value = hsv[0],
        range = 0f..360f,
        onValueChange = { h ->
            hsv[0] = h
            onColorChange(AndroidColor.HSVToColor(hsv))
        }
    )
    LuxurySlider(
        label = "تشبع اللون (Saturation)",
        valueText = "${(hsv[1] * 100).roundToInt()}%",
        value = hsv[1],
        range = 0f..1f,
        onValueChange = { s ->
            hsv[1] = s
            onColorChange(AndroidColor.HSVToColor(hsv))
        }
    )
    LuxurySlider(
        label = "سطوع اللون (Brightness)",
        valueText = "${(hsv[2] * 100).roundToInt()}%",
        value = hsv[2],
        range = 0.25f..1f,
        onValueChange = { v ->
            hsv[2] = v
            onColorChange(AndroidColor.HSVToColor(hsv))
        }
    )
}

@Composable
private fun ThreeDBackgroundTab(
    config: KepordConfig,
    onUpdate: ((KepordConfig) -> KepordConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LiveKeyboardEnginePreview(config = config, arabicPreview = config.defaultLanguageArabic)

        LuxuryCard(title = "خلفية الكيبورد ثلاثية الأبعاد (أسود وأبيض)") {
            Background3DStyle.entries.forEach { style ->
                SelectableRow(
                    title = style.titleAr,
                    subtitle = style.descAr,
                    selected = config.background3DStyle == style,
                    onClick = { onUpdate { it.copy(background3DStyle = style) } }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LuxurySlider(
                label = "وضوح وقوة خطوط الخلفية 3D",
                valueText = "${(config.bg3DIntensity * 100).roundToInt()}%",
                value = config.bg3DIntensity,
                range = 0.15f..1.0f,
                onValueChange = { v -> onUpdate { it.copy(bg3DIntensity = v) } }
            )
        }

        LuxuryCard(title = "عزل الأزرار وبروز الأحرف 3D") {
            LuxurySlider(
                label = "درجة عتامة غطاء الزر (لحماية وضوح الأحرف فوق الخلفية 3D)",
                valueText = "${(config.keycapOpacity * 100).roundToInt()}%",
                value = config.keycapOpacity,
                range = 0.72f..1.0f,
                onValueChange = { v -> onUpdate { it.copy(keycapOpacity = v) } }
            )
            LuxurySlider(
                label = "بروز الأحرف ثلاثي الأبعاد (3D Letter Extrusion)",
                valueText = "مستوى ${config.letter3DDepth}",
                value = config.letter3DDepth.toFloat(),
                range = 0f..6f,
                steps = 5,
                onValueChange = { v -> onUpdate { it.copy(letter3DDepth = v.roundToInt()) } }
            )
            LuxurySlider(
                label = "عمق وارتفاع الزر 3D (Keycap Bevel)",
                valueText = "${config.keyBevelDepth} dp",
                value = config.keyBevelDepth.toFloat(),
                range = 0f..5f,
                steps = 4,
                onValueChange = { v -> onUpdate { it.copy(keyBevelDepth = v.roundToInt()) } }
            )
            LuxurySlider(
                label = "استدارة زوايا الأزرار",
                valueText = "${config.keyCornerRadiusDp.roundToInt()} dp",
                value = config.keyCornerRadiusDp,
                range = 3f..16f,
                onValueChange = { v -> onUpdate { it.copy(keyCornerRadiusDp = v) } }
            )
            LuxurySwitchRow(
                title = "إظهار حدود الأزرار الدقيقة",
                subtitle = "يزيد فصل كل مفتاح بوضوح عن الخلفية ثلاثية الأبعاد",
                checked = config.showKeyBorders,
                onCheckedChange = { b -> onUpdate { it.copy(showKeyBorders = b) } }
            )
            LuxurySwitchRow(
                title = "إظهار التلميحات الثانوية على الزر",
                subtitle = "عرض الحركات أو الأرقام الصغيرة أعلى يمين الزر",
                checked = config.showSecondaryHints,
                onCheckedChange = { b -> onUpdate { it.copy(showSecondaryHints = b) } }
            )
        }
    }
}

@Composable
private fun FontsAndSystemTab(
    config: KepordConfig,
    onUpdate: ((KepordConfig) -> KepordConfig) -> Unit,
    onResetDefaults: () -> Unit
) {
    val samplePhrase = "مرحبا بكم في كيبورد Kepord الفاخر"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LuxuryCard(title = "أشهر 10 خطوط عربية للكتابة (تبقى فعالة حتى تغيرها)") {
            Text(
                text = "الخط الذي تختاره هنا سيكتب به الكيبورد تلقائياً في واتساب وتيليجرام وكل التطبيقات:",
                color = KepordPalette.MutedGray,
                fontSize = 11.5.sp
            )

            ArabicFontStyle.entries.forEach { font ->
                val selected = config.arabicFontStyle == font
                val preview = remember(font) { ArabicFontEngine.transformFullText(samplePhrase, font) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdate { it.copy(arabicFontStyle = font) } },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) KepordPalette.SurfaceElevated else KepordPalette.PureBlack,
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) KepordPalette.PureWhite else KepordPalette.BorderHairline
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(font.titleAr, color = KepordPalette.PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(font.descAr, color = KepordPalette.MutedGray, fontSize = 10.5.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preview,
                                color = KepordPalette.SoftWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (selected) KepordPalette.PureWhite else Color.Transparent)
                                .border(1.5.dp, KepordPalette.PureWhite, CircleShape)
                        )
                    }
                }
            }
        }

        LuxuryCard(title = "التخطيط واللغة والأبعاد") {
            LuxurySwitchRow(
                title = "لغة البدء الافتراضية: العربية",
                subtitle = "زر AR/EN داخل الكيبورد يبدّل اللغة فوراً في أي وقت",
                checked = config.defaultLanguageArabic,
                onCheckedChange = { b -> onUpdate { it.copy(defaultLanguageArabic = b) } }
            )
            LuxurySwitchRow(
                title = "صف الأرقام الدائم في الأعلى (1 - 0)",
                subtitle = "صف أرقام ثابت أعلى لوحة الحروف مع بدائل بالضغط المطوّل",
                checked = config.showNumberRow,
                onCheckedChange = { b -> onUpdate { it.copy(showNumberRow = b) } }
            )
            LuxurySlider(
                label = "ارتفاع لوحة المفاتيح",
                valueText = "${(config.keyboardHeightScale * 100).roundToInt()}%",
                value = config.keyboardHeightScale,
                range = 0.80f..1.25f,
                onValueChange = { v -> onUpdate { it.copy(keyboardHeightScale = v) } }
            )
            LuxurySlider(
                label = "حجم خط الأحرف",
                valueText = "${(config.letterSizeScale * 100).roundToInt()}%",
                value = config.letterSizeScale,
                range = 0.80f..1.35f,
                onValueChange = { v -> onUpdate { it.copy(letterSizeScale = v) } }
            )
        }

        LuxuryCard(title = "التفاعل والاهتزاز والصوت (Realme GT)") {
            LuxurySlider(
                label = "قوة الاهتزاز عند الضغط (Haptics)",
                valueText = if (config.hapticStrengthMs == 0) "مغلق" else "${config.hapticStrengthMs} ms",
                value = config.hapticStrengthMs.toFloat(),
                range = 0f..45f,
                onValueChange = { v -> onUpdate { it.copy(hapticStrengthMs = v.roundToInt()) } }
            )
            LuxurySwitchRow(
                title = "صوت النقر عند الكتابة",
                subtitle = "نغمة نقر ميكانيكية خفيفة متزامنة مع اللمس",
                checked = config.keySoundEnabled,
                onCheckedChange = { b -> onUpdate { it.copy(keySoundEnabled = b) } }
            )
            if (config.keySoundEnabled) {
                LuxurySlider(
                    label = "مستوى صوت النقر",
                    valueText = "${(config.keySoundVolume * 100).roundToInt()}%",
                    value = config.keySoundVolume,
                    range = 0.1f..1.0f,
                    onValueChange = { v -> onUpdate { it.copy(keySoundVolume = v) } }
                )
            }
            LuxurySwitchRow(
                title = "نافذة تكبير الحرف عند اللمس (Popup)",
                subtitle = "إظهار الحرف 3D مكبراً فوق إصبعك أثناء النقر",
                checked = config.keyPopupEnabled,
                onCheckedChange = { b -> onUpdate { it.copy(keyPopupEnabled = b) } }
            )
            LuxurySwitchRow(
                title = "تحريك المؤشر بالسحب على المسافة",
                subtitle = "اسحب يميناً أو يساراً على زر المسافة لتحريك مؤشر الكتابة بدقة",
                checked = config.spacebarCursorSwipe,
                onCheckedChange = { b -> onUpdate { it.copy(spacebarCursorSwipe = b) } }
            )
        }

        LuxuryCard(title = "الكتابة الذكية") {
            LuxurySwitchRow(
                title = "حرف كبير تلقائي في الإنجليزية (Auto-Caps)",
                subtitle = "تفعيل Shift تلقائياً في بداية الجمل الإنجليزية",
                checked = config.autoCapsEnglish,
                onCheckedChange = { b -> onUpdate { it.copy(autoCapsEnglish = b) } }
            )
            LuxurySwitchRow(
                title = "نقطة عند الضغط مرتين على المسافة",
                subtitle = "تحويل المسافتين السريعتين إلى نقطة ومسافة",
                checked = config.doubleSpacePeriod,
                onCheckedChange = { b -> onUpdate { it.copy(doubleSpacePeriod = b) } }
            )
        }

        OutlinedButton(
            onClick = onResetDefaults,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, KepordPalette.PureWhite),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = KepordPalette.PureWhite)
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("استعادة جميع الإعدادات الافتراضية", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ClipboardVaultTab(
    config: KepordConfig,
    clips: List<ClipEntry>,
    clipboardStore: ClipboardStore,
    onUpdateConfig: ((KepordConfig) -> KepordConfig) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var inspectedEntry by remember { mutableStateOf<ClipEntry?>(null) }
    var inspectedLines by remember { mutableStateOf<List<String>>(emptyList()) }

    val filtered = remember(clips, searchQuery) {
        if (searchQuery.isBlank()) clips
        else clips.filter { it.preview.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LuxuryCard(title = "إعدادات الحافظة اللانهائية (تبقى بعد إعادة تشغيل الهاتف)") {
                LuxurySlider(
                    label = "عدد العناصر غير المثبتة المحفوظة تلقائياً",
                    valueText = "${config.clipboardMaxUnpinned} عنصر",
                    value = config.clipboardMaxUnpinned.toFloat(),
                    range = 20f..200f,
                    onValueChange = { v -> onUpdateConfig { it.copy(clipboardMaxUnpinned = v.roundToInt()) } }
                )
                LuxurySwitchRow(
                    title = "المراقبة التلقائية لكل ما يتم نسخه",
                    subtitle = "يحفظ آلاف الأسطر البرمجية والنصوص بدون قص أي حرف",
                    checked = config.clipboardMonitorEnabled,
                    onCheckedChange = { b -> onUpdateConfig { it.copy(clipboardMonitorEnabled = b) } }
                )
                LuxurySwitchRow(
                    title = "حماية كلمات المرور في Android 13",
                    subtitle = "تجاهل المحتوى الموسوم كحساس (IS_SENSITIVE) تلقائياً",
                    checked = config.clipboardSkipSensitive,
                    onCheckedChange = { b -> onUpdateConfig { it.copy(clipboardSkipSensitive = b) } }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalBytes = clips.sumOf { it.byteSize }
                    Text(
                        text = "المحفوظ: ${clips.size} عنصر (${String.format("%.1f KB", totalBytes / 1024.0)})",
                        color = KepordPalette.MutedGray,
                        fontSize = 11.5.sp
                    )
                    OutlinedButton(
                        onClick = { clipboardStore.clearUnpinnedAsync() },
                        border = BorderStroke(1.dp, KepordPalette.BorderStrong),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KepordPalette.PureWhite)
                    ) {
                        Text("مسح غير المثبت", fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White) },
                placeholder = { Text("ابحث داخل الحافظة...", color = KepordPalette.MutedGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KepordPalette.PureWhite,
                    unfocusedBorderColor = KepordPalette.BorderStrong,
                    focusedTextColor = KepordPalette.PureWhite,
                    unfocusedTextColor = KepordPalette.PureWhite,
                    cursorColor = KepordPalette.PureWhite,
                    focusedContainerColor = KepordPalette.SurfaceCard,
                    unfocusedContainerColor = KepordPalette.SurfaceCard
                )
            )
        }

        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد عناصر مطابقة في الحافظة الآن.",
                        color = KepordPalette.MutedGray,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { entry ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KepordPalette.SurfaceCard,
                    border = BorderStroke(1.dp, if (entry.isPinned) KepordPalette.PureWhite else KepordPalette.BorderHairline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = entry.preview,
                            color = KepordPalette.PureWhite,
                            fontSize = 13.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${entry.lineCount} سطر • ${entry.charCount} حرف • ${String.format("%.1f KB", entry.byteSize / 1024.0)}",
                                color = KepordPalette.MutedGray,
                                fontSize = 11.sp
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val full = clipboardStore.readFullText(entry)
                                            inspectedLines = full.split('\n').chunked(35).map { it.joinToString("\n") }
                                            inspectedEntry = entry
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Rounded.Visibility, contentDescription = "معاينة كاملة", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val full = clipboardStore.readFullText(entry)
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("kepord_clip", full))
                                            Toast.makeText(context, "تم نسخ النص كاملاً (${entry.lineCount} سطر)", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "نسخ", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { clipboardStore.togglePinAsync(entry.id) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.PushPin,
                                        contentDescription = "تثبيت",
                                        tint = if (entry.isPinned) Color.White else KepordPalette.MutedGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { clipboardStore.deleteEntryAsync(entry) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "حذف", tint = KepordPalette.MutedGray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val currentInspect = inspectedEntry
    if (currentInspect != null) {
        Dialog(
            onDismissRequest = { inspectedEntry = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                shape = RoundedCornerShape(16.dp),
                color = KepordPalette.PureBlack,
                border = BorderStroke(1.dp, KepordPalette.PureWhite)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("المحتوى الكامل (بدون قص)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "${currentInspect.lineCount} سطر • ${currentInspect.charCount} حرف",
                                color = KepordPalette.MutedGray,
                                fontSize = 11.5.sp
                            )
                        }
                        IconButton(onClick = { inspectedEntry = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "إغلاق", tint = Color.White)
                        }
                    }
                    HorizontalDivider(color = KepordPalette.BorderHairline, modifier = Modifier.padding(vertical = 8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(KepordPalette.SurfaceCard, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        items(inspectedLines) { block ->
                            Text(
                                text = block,
                                color = KepordPalette.SoftWhite,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val full = clipboardStore.readFullText(currentInspect)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("kepord_full", full))
                                Toast.makeText(context, "تم النسخ كاملاً!", Toast.LENGTH_SHORT).show()
                                inspectedEntry = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("نسخ النص كاملاً الآن", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LuxuryCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = KepordPalette.SurfaceCard,
        border = BorderStroke(1.dp, KepordPalette.BorderHairline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = KepordPalette.PureWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(color = KepordPalette.BorderHairline)
            content()
        }
    }
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) KepordPalette.SurfaceElevated else KepordPalette.PureBlack,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) KepordPalette.PureWhite else KepordPalette.BorderHairline
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = KepordPalette.PureWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = KepordPalette.MutedGray, fontSize = 11.sp)
                }
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (selected) KepordPalette.PureWhite else Color.Transparent)
                    .border(1.5.dp, KepordPalette.PureWhite, CircleShape)
            )
        }
    }
}

@Composable
private fun LuxurySlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = KepordPalette.SoftWhite, fontSize = 12.5.sp)
            Text(valueText, color = KepordPalette.PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = KepordPalette.PureWhite,
                activeTrackColor = KepordPalette.PureWhite,
                inactiveTrackColor = KepordPalette.BorderStrong
            )
        )
    }
}

@Composable
private fun LuxurySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = KepordPalette.SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = KepordPalette.MutedGray, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = KepordPalette.PureBlack,
                checkedTrackColor = KepordPalette.PureWhite,
                uncheckedThumbColor = KepordPalette.PureWhite,
                uncheckedTrackColor = KepordPalette.SurfaceElevated,
                uncheckedBorderColor = KepordPalette.BorderStrong
            )
        )
    }
}
