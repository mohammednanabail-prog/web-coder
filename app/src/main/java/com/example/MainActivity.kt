package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.FileNode
import com.example.ui.HighlightUtils
import com.example.ui.MainViewModel
import com.example.ui.SyntaxHighlightTransformation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureWebViewCacheDirs()
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(application))
            val isDarkMode by mainViewModel.isDarkMode.collectAsStateWithLifecycle()

            // High Density Developer Theme colors (Slate-dark, crisp blue accents, sleek borders)
            val primaryDark = Color(0xFF58A6FF)     // Interactive bright developer blue
            val secondaryDark = Color(0xFF010409)   // Obsidian sidebar/bottom panel background
            val backgroundDark = Color(0xFF0D1117)  // Deep slate workspace background
            val surfaceDark = Color(0xFF161B22)     // Slate header & tab surface
            val borderDark = Color(0xFF30363D)      // Precise high-density border line

            val primaryLight = Color(0xFF0969DA)    // GitHub light blue accent
            val secondaryLight = Color(0xFFF6F8FA)  // Sidebar off-white background
            val backgroundLight = Color(0xFFFFFFFF) // Clean canvas background
            val surfaceLight = Color(0xFFF6F8FA)    // Light slate panels
            val borderLight = Color(0xFFD0D7DE)     // Clean light gray divider border

            val colorScheme = if (isDarkMode) {
                darkColorScheme(
                    primary = primaryDark,
                    secondary = secondaryDark,
                    background = backgroundDark,
                    surface = surfaceDark,
                    outline = borderDark,
                    onPrimary = Color.White,
                    onSecondary = Color(0xFFC9D1D9),
                    onBackground = Color(0xFFC9D1D9),
                    onSurface = Color(0xFFE1E4E8)
                )
            } else {
                lightColorScheme(
                    primary = primaryLight,
                    secondary = secondaryLight,
                    background = backgroundLight,
                    surface = surfaceLight,
                    outline = borderLight,
                    onPrimary = Color.White,
                    onSecondary = Color(0xFF24292F),
                    onBackground = Color(0xFF24292F),
                    onSurface = Color(0xFF24292F)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                WebEditorApp(viewModel = mainViewModel)
            }
        }
    }

    private fun ensureWebViewCacheDirs() {
        try {
            val cachePath = cacheDir.absolutePath
            val wasmDir = java.io.File("$cachePath/WebView/Default/HTTP Cache/Code Cache/wasm")
            val jsDir = java.io.File("$cachePath/WebView/Default/HTTP Cache/Code Cache/js")
            if (!wasmDir.exists()) {
                wasmDir.mkdirs()
            }
            if (!jsDir.exists()) {
                jsDir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebEditorApp(viewModel: MainViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    
    val allNodes by viewModel.allNodes.collectAsStateWithLifecycle()
    val activeFile by viewModel.activeFile.collectAsStateWithLifecycle()
    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    
    // View modes: "split" or "tabs"
    var viewMode by remember { mutableStateOf("split") }
    // Selected tab when in tabs view mode: "code" or "preview"
    var activeTab by remember { mutableStateOf("code") }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = Modifier.testTag("file_manager_drawer"),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.secondary,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(310.dp)
            ) {
                FileExplorerPanel(
                    viewModel = viewModel,
                    allNodes = allNodes,
                    currentFolderId = currentFolderId,
                    onCloseDrawer = { coroutineScope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "WebCode Craft",
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open File Explorer"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleTheme() }) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme"
                            )
                        }
                        IconButton(onClick = {
                            viewMode = if (viewMode == "split") "tabs" else "split"
                        }) {
                            Icon(
                                imageVector = if (viewMode == "split") Icons.Default.ViewAgenda else Icons.Default.VerticalSplit,
                                contentDescription = "Toggle View Layout"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Active file title bar
                ActiveFileIndicator(activeFile = activeFile, onOpenExplorer = {
                    coroutineScope.launch { drawerState.open() }
                })

                // Tab selectors when in Tab view mode
                if (viewMode == "tabs") {
                    TabRow(
                        selectedTabIndex = if (activeTab == "code") 0 else 1,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = activeTab == "code",
                            onClick = { activeTab = "code" },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("محرر الأكواد", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        Tab(
                            selected = activeTab == "preview",
                            onClick = { activeTab = "preview" },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("المعاينة الحية", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }

                // Workspace content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (viewMode == "split") {
                        // Split screen layout
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top half: Editor
                            Box(modifier = Modifier.weight(1.1f)) {
                                CodeEditorContainer(viewModel = viewModel)
                            }
                            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), thickness = 1.dp)
                            // Bottom half: Live Preview
                            Box(modifier = Modifier.weight(0.9f)) {
                                LivePreviewContainer(viewModel = viewModel)
                            }
                        }
                    } else {
                        // Full tabs layout
                        if (activeTab == "code") {
                            CodeEditorContainer(viewModel = viewModel)
                        } else {
                            LivePreviewContainer(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    DialogsManager(viewModel = viewModel)
}

@Composable
fun ActiveFileIndicator(activeFile: FileNode?, onOpenExplorer: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        activeFile == null -> Icons.Default.FolderOpen
                        activeFile.name.endsWith(".html", ignoreCase = true) -> Icons.Default.Html
                        activeFile.name.endsWith(".css", ignoreCase = true) -> Icons.Default.Css
                        activeFile.name.endsWith(".js", ignoreCase = true) -> Icons.Default.Javascript
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = when {
                        activeFile == null -> MaterialTheme.colorScheme.primary
                        activeFile.name.endsWith(".html", ignoreCase = true) -> Color(0xFFEF4444)
                        activeFile.name.endsWith(".css", ignoreCase = true) -> Color(0xFF3B82F6)
                        activeFile.name.endsWith(".js", ignoreCase = true) -> Color(0xFFF59E0B)
                        else -> Color.Gray
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = activeFile?.name ?: "لم يتم فتح أي ملف",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (activeFile != null) {
                        Text(
                            text = if (activeFile.name.endsWith(".html", ignoreCase = true)) "تلوين تلقائي (HTML)" 
                            else if (activeFile.name.endsWith(".css", ignoreCase = true)) "تلوين تلقائي (CSS)"
                            else if (activeFile.name.endsWith(".js", ignoreCase = true)) "تلوين تلقائي (JavaScript)"
                            else "نص عادي",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            if (activeFile == null) {
                Button(
                    onClick = onOpenExplorer,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("فتح مستكشف الملفات", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FileExplorerPanel(
    viewModel: MainViewModel,
    allNodes: List<FileNode>,
    currentFolderId: Int?,
    onCloseDrawer: () -> Unit
) {
    val currentParent = allNodes.find { it.id == currentFolderId }
    val currentLevelNodes = allNodes.filter { it.parentId == currentFolderId }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "مستكشف المشاريع",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Row {
                IconButton(onClick = { viewModel.openCreateFileDialog(true) }) {
                    Icon(Icons.Outlined.NoteAdd, contentDescription = "ملف جديد", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.openCreateFolderDialog(true) }) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = "مجلد جديد", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Back navigation if in a subfolder
        if (currentFolderId != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .clickable { viewModel.navigateToFolder(currentParent?.parentId) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ".. / (${currentParent?.name ?: "الرئيسية"})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Tree structure list
        if (currentLevelNodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "هذا المجلد فارغ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(currentLevelNodes, key = { it.id }) { node ->
                    FileRowItem(
                        node = node,
                        onSelectFile = {
                            viewModel.selectFile(node)
                            onCloseDrawer()
                        },
                        onOpenFolder = { viewModel.navigateToFolder(node.id) },
                        onRename = { viewModel.openRenameDialog(node) },
                        onDelete = { viewModel.deleteNode(node) }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Settings / Local Database status info
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "مخزن محلي آمن ومحفوظ تلقائياً",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun FileRowItem(
    node: FileNode,
    onSelectFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (node.isFolder) MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable {
                if (node.isFolder) onOpenFolder() else onSelectFile()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = when {
                    node.isFolder -> Icons.Default.Folder
                    node.name.endsWith(".html", ignoreCase = true) -> Icons.Default.Html
                    node.name.endsWith(".css", ignoreCase = true) -> Icons.Default.Css
                    node.name.endsWith(".js", ignoreCase = true) -> Icons.Default.Javascript
                    else -> Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                tint = when {
                    node.isFolder -> Color(0xFFFBBF24) // Amber Folder
                    node.name.endsWith(".html", ignoreCase = true) -> Color(0xFFEF4444) // Red HTML
                    node.name.endsWith(".css", ignoreCase = true) -> Color(0xFF3B82F6) // Blue CSS
                    node.name.endsWith(".js", ignoreCase = true) -> Color(0xFFF59E0B) // Amber JS
                    else -> Color.Gray
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = node.name,
                    fontWeight = if (node.isFolder) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        // Operations Menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "خيارات الملف", modifier = Modifier.size(16.dp))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("تغيير الاسم", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("حذف", fontSize = 13.sp, color = Color.Red) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
fun CodeEditorContainer(viewModel: MainViewModel) {
    val activeFile by viewModel.activeFile.collectAsStateWithLifecycle()
    val codeContent by viewModel.editorContent.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()
    
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    // Synchronize content on active file swap
    LaunchedEffect(activeFile?.id) {
        textFieldValue = TextFieldValue(
            text = codeContent,
            selection = TextRange(codeContent.length)
        )
    }

    if (activeFile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(
                    imageVector = Icons.Default.DataObject,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "حدد ملفاً من القائمة الجانبية لبدء البرمجة",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Editor body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val lines = textFieldValue.text.split('\n')
                val lineScrollState = rememberScrollState()
                
                Row(modifier = Modifier.fillMaxSize()) {
                    // Line Numbers Panel
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(42.dp)
                            .background(MaterialTheme.colorScheme.secondary)
                            .verticalScroll(lineScrollState)
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        lines.forEachIndexed { index, _ ->
                            Text(
                                text = "${index + 1}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(end = 8.dp, bottom = 2.dp)
                            )
                        }
                    }

                    VerticalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                    // Text Input
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newVal ->
                            textFieldValue = newVal
                            if (newVal.text != codeContent) {
                                viewModel.updateEditorContent(newVal.text)
                            }
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 18.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = SyntaxHighlightTransformation(
                            extension = activeFile?.name?.substringAfterLast('.', "") ?: "",
                            isDark = isDarkTheme
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(lineScrollState)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .testTag("editor_text_field")
                    )
                }
            }

            // Keyboard Assist Toolbar
            CodingToolbar(
                onKeyPress = { char ->
                    val originalText = textFieldValue.text
                    val selectionStart = textFieldValue.selection.start
                    val selectionEnd = textFieldValue.selection.end
                    val before = originalText.substring(0, selectionStart)
                    val after = originalText.substring(selectionEnd)
                    val newText = before + char + after
                    val newSelection = TextRange(selectionStart + char.length)
                    
                    textFieldValue = TextFieldValue(newText, newSelection)
                    viewModel.updateEditorContent(newText)
                }
            )
        }
    }
}

@Composable
fun CodingToolbar(onKeyPress: (String) -> Unit) {
    val characters = listOf("<", ">", "{", "}", "[", "]", ";", "\"", "/", "=", "!", ".", "(", ")")
    
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            characters.forEach { char ->
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .clickable { onKeyPress(char) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LivePreviewContainer(viewModel: MainViewModel) {
    val liveHtml by viewModel.livePreviewHtml.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Preview Header info bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "المعاينة التلقائية الفورية",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
                )
            }
        }
        
        // Android WebView to execute HTML/CSS/JS code
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                }
            },
            update = { webView ->
                // Use a standard Base URL so JavaScript and references behave reliably
                webView.loadDataWithBaseURL(
                    "http://localhost/",
                    liveHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        )
    }
}

@Composable
fun DialogsManager(viewModel: MainViewModel) {
    val isCreateFileDialogOpen by viewModel.isCreateFileDialogOpen.collectAsStateWithLifecycle()
    val isCreateFolderDialogOpen by viewModel.isCreateFolderDialogOpen.collectAsStateWithLifecycle()
    val isRenameDialogOpen by viewModel.isRenameDialogOpen.collectAsStateWithLifecycle()
    
    // Create File Dialog
    if (isCreateFileDialogOpen) {
        var fileName by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { viewModel.openCreateFileDialog(false) },
            title = { Text("إنشاء ملف جديد") },
            text = {
                Column {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { 
                            fileName = it
                            errorMessage = ""
                        },
                        label = { Text("اسم الملف (مثال: index.html)") },
                        singleLine = true,
                        isError = errorMessage.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("create_file_input")
                    )
                    if (errorMessage.isNotEmpty()) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val extension = fileName.substringAfterLast('.', "")
                        if (fileName.isEmpty()) {
                            errorMessage = "لا يمكن أن يكون الاسم فارغاً"
                        } else if (!setOf("html", "css", "js").contains(extension.lowercase())) {
                            errorMessage = "الامتداد يجب أن يكون .html أو .css أو .js"
                        } else {
                            viewModel.createFile(fileName)
                            viewModel.openCreateFileDialog(false)
                        }
                    }
                ) {
                    Text("إنشاء")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.openCreateFileDialog(false) }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Create Folder Dialog
    if (isCreateFolderDialogOpen) {
        var folderName by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { viewModel.openCreateFolderDialog(false) },
            title = { Text("إنشاء مجلد جديد") },
            text = {
                Column {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { 
                            folderName = it
                            errorMessage = ""
                        },
                        label = { Text("اسم المجلد") },
                        singleLine = true,
                        isError = errorMessage.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("create_folder_input")
                    )
                    if (errorMessage.isNotEmpty()) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isEmpty()) {
                            errorMessage = "لا يمكن أن يكون اسم المجلد فارغاً"
                        } else {
                            viewModel.createFolder(folderName)
                            viewModel.openCreateFolderDialog(false)
                        }
                    }
                ) {
                    Text("إنشاء")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.openCreateFolderDialog(false) }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Rename Dialog
    val renameTarget = isRenameDialogOpen
    if (renameTarget != null) {
        var newName by remember { mutableStateOf(renameTarget.name) }
        var errorMessage by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { viewModel.openRenameDialog(null) },
            title = { Text("تغيير اسم الملف/المجلد") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { 
                            newName = it
                            errorMessage = ""
                        },
                        label = { Text("الاسم الجديد") },
                        singleLine = true,
                        isError = errorMessage.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("rename_node_input")
                    )
                    if (errorMessage.isNotEmpty()) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val extension = newName.substringAfterLast('.', "")
                        if (newName.isEmpty()) {
                            errorMessage = "لا يمكن أن يكون الاسم فارغاً"
                        } else if (!renameTarget.isFolder && !setOf("html", "css", "js").contains(extension.lowercase())) {
                            errorMessage = "الامتداد يجب أن يكون .html أو .css أو .js"
                        } else {
                            viewModel.renameNode(renameTarget, newName)
                            viewModel.openRenameDialog(null)
                        }
                    }
                ) {
                    Text("تعديل")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.openRenameDialog(null) }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
