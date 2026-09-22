package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FileNode
import com.example.data.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FileRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FileRepository(database.fileNodeDao())
        
        // Initialize sample project if DB is empty
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.getNodeCount() == 0) {
                populateSampleProject()
            } else {
                // Pre-select first HTML file found or any file to keep starting UI beautiful
                repository.allNodes.collect { nodes ->
                    if (nodes.isNotEmpty() && _activeFile.value == null) {
                        val firstHtml = nodes.find { it.name.endsWith(".html", ignoreCase = true) }
                        val selected = firstHtml ?: nodes.firstOrNull { !it.isFolder }
                        viewModelScope.launch(Dispatchers.Main) {
                            if (selected != null) {
                                selectFile(selected)
                                if (selected.parentId != null) {
                                    navigateToFolder(selected.parentId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val allNodes: StateFlow<List<FileNode>> = repository.allNodes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current editing file State Flow
    private val _activeFile = MutableStateFlow<FileNode?>(null)
    val activeFile = _activeFile.asStateFlow()

    // Editor content state to support fast real-time typing without lagging behind Room writes
    private val _editorContent = MutableStateFlow("")
    val editorContent = _editorContent.asStateFlow()

    // Dark/Light Theme mode
    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode = _isDarkMode.asStateFlow()

    // Current Folder ID navigation state for File Manager (null means root)
    private val _currentFolderId = MutableStateFlow<Int?>(null)
    val currentFolderId = _currentFolderId.asStateFlow()

    // Dialog & UI states
    private val _isCreateFileDialogOpen = MutableStateFlow(false)
    val isCreateFileDialogOpen = _isCreateFileDialogOpen.asStateFlow()

    private val _isCreateFolderDialogOpen = MutableStateFlow(false)
    val isCreateFolderDialogOpen = _isCreateFolderDialogOpen.asStateFlow()

    private val _isRenameDialogOpen = MutableStateFlow<FileNode?>(null)
    val isRenameDialogOpen = _isRenameDialogOpen.asStateFlow()

    // Preview code combined state
    val livePreviewHtml: StateFlow<String> = combine(_activeFile, _editorContent, allNodes) { active, code, nodes ->
        if (active == null) {
            return@combine "<h3>No file open. Select or create an HTML file to preview.</h3>"
        }
        if (!active.name.endsWith(".html", ignoreCase = true)) {
            // Find first HTML file in sibling or general hierarchy to show as preview fallback
            val siblingHtml = nodes.find { it.parentId == active.parentId && it.name.endsWith(".html", ignoreCase = true) }
            if (siblingHtml != null) {
                return@combine getBundledHtml(siblingHtml, nodes, active.id, code)
            }
            // General fallback: first HTML in the whole app
            val generalHtml = nodes.find { it.name.endsWith(".html", ignoreCase = true) }
            if (generalHtml != null) {
                return@combine getBundledHtml(generalHtml, nodes, active.id, code)
            }
            return@combine "<h3>Select an HTML file to preview directly.</h3>"
        }
        // Active file is HTML
        getBundledHtml(active, nodes, active.id, code)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    fun toggleTheme() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun selectFile(node: FileNode?) {
        _activeFile.value = node
        _editorContent.value = node?.content ?: ""
    }

    fun updateEditorContent(newContent: String) {
        _editorContent.value = newContent
        val current = _activeFile.value
        if (current != null) {
            // Update in-memory content of active file so that database list & preview gets it
            _activeFile.value = current.copy(content = newContent)
            // Save to DB on background thread
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateNode(current.copy(content = newContent, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun navigateToFolder(folderId: Int?) {
        _currentFolderId.value = folderId
    }

    fun openCreateFileDialog(show: Boolean) {
        _isCreateFileDialogOpen.value = show
    }

    fun openCreateFolderDialog(show: Boolean) {
        _isCreateFolderDialogOpen.value = show
    }

    fun openRenameDialog(node: FileNode?) {
        _isRenameDialogOpen.value = node
    }

    fun createFile(name: String) {
        val parentId = _currentFolderId.value
        viewModelScope.launch(Dispatchers.IO) {
            val defaultContent = when {
                name.endsWith(".html", ignoreCase = true) -> """<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>New Document</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <h1>Hello World</h1>
    <script src="script.js"></script>
</body>
</html>"""
                name.endsWith(".css", ignoreCase = true) -> """body {
    background-color: #121212;
    color: #ffffff;
    font-family: sans-serif;
}"""
                name.endsWith(".js", ignoreCase = true) -> "console.log('Hello World');"
                else -> ""
            }
            val newNode = FileNode(
                name = name,
                parentId = parentId,
                isFolder = false,
                content = defaultContent
            )
            val id = repository.insertNode(newNode)
            val insertedNode = newNode.copy(id = id)
            // Automatically select the newly created file
            viewModelScope.launch(Dispatchers.Main) {
                selectFile(insertedNode)
            }
        }
    }

    fun createFolder(name: String) {
        val parentId = _currentFolderId.value
        viewModelScope.launch(Dispatchers.IO) {
            val newNode = FileNode(
                name = name,
                parentId = parentId,
                isFolder = true
            )
            repository.insertNode(newNode)
        }
    }

    fun renameNode(node: FileNode, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = node.copy(name = newName, updatedAt = System.currentTimeMillis())
            repository.updateNode(updated)
            if (_activeFile.value?.id == node.id) {
                _activeFile.value = updated
            }
        }
    }

    fun deleteNode(node: FileNode) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteNode(node)
            if (_activeFile.value?.id == node.id) {
                viewModelScope.launch(Dispatchers.Main) {
                    selectFile(null)
                }
            }
        }
    }

    private suspend fun populateSampleProject() {
        // Root Folder
        val rootFolderId = repository.insertNode(
            FileNode(name = "My Portfolio", isFolder = true, parentId = null)
        )

        // HTML File
        val htmlContent = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Smart Web Space</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div class="card">
        <div class="logo">✦</div>
        <h1>WebCode Craft</h1>
        <p>Welcome to your professional mobile development workspace. Edit HTML, CSS, and JS in real-time, instantly visualized below.</p>
        
        <div class="actions">
            <button id="actionBtn" class="btn">Touch Magic</button>
        </div>
        
        <div class="stats">
            <div class="stat-item">
                <span class="num">100%</span>
                <span class="lbl">Offline</span>
            </div>
            <div class="stat-item">
                <span class="num">Live</span>
                <span class="lbl">Preview</span>
            </div>
        </div>
    </div>
    
    <script src="script.js"></script>
</body>
</html>"""

        val htmlNodeId = repository.insertNode(
            FileNode(name = "index.html", parentId = rootFolderId, isFolder = false, content = htmlContent)
        )

        // CSS File
        val cssContent = """body {
    margin: 0;
    padding: 0;
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
    background: radial-gradient(circle at 50% 50%, #1e1e30 0%, #0c0c14 100%);
    font-family: 'Segoe UI', system-ui, sans-serif;
    color: #e2e8f0;
    overflow-x: hidden;
}

.card {
    background: rgba(30, 30, 50, 0.45);
    border: 1px solid rgba(255, 255, 255, 0.08);
    backdrop-filter: blur(20px);
    border-radius: 24px;
    padding: 40px 30px;
    width: 90%;
    max-width: 380px;
    text-align: center;
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.4);
    transition: transform 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.card:hover {
    transform: translateY(-8px);
}

.logo {
    font-size: 48px;
    background: linear-gradient(135deg, #a78bfa 0%, #ec4899 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    margin-bottom: 20px;
    animation: rotate 10s linear infinite;
}

h1 {
    font-size: 28px;
    font-weight: 800;
    margin: 0 0 12px 0;
    letter-spacing: -0.5px;
    background: linear-gradient(135deg, #ffffff 0%, #cbd5e1 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
}

p {
    font-size: 14px;
    line-height: 1.6;
    color: #94a3b8;
    margin-bottom: 30px;
}

.btn {
    background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
    color: white;
    border: none;
    padding: 14px 28px;
    font-size: 15px;
    font-weight: 600;
    border-radius: 50px;
    cursor: pointer;
    box-shadow: 0 10px 20px rgba(99, 102, 241, 0.3);
    transition: all 0.3s ease;
}

.btn:active {
    transform: scale(0.95);
    box-shadow: 0 5px 10px rgba(99, 102, 241, 0.3);
}

.stats {
    display: flex;
    justify-content: space-around;
    margin-top: 35px;
    padding-top: 25px;
    border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.stat-item {
    display: flex;
    flex-direction: column;
}

.num {
    font-size: 20px;
    font-weight: 700;
    color: #818cf8;
}

.lbl {
    font-size: 11px;
    text-transform: uppercase;
    color: #64748b;
    letter-spacing: 1px;
    margin-top: 4px;
}

@keyframes rotate {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
}"""

        repository.insertNode(
            FileNode(name = "style.css", parentId = rootFolderId, isFolder = false, content = cssContent)
        )

        // JS File
        val jsContent = """const btn = document.getElementById('actionBtn');
const card = document.querySelector('.card');

btn.addEventListener('click', () => {
    // Generate beautiful random gradient
    const colors = [
        ['#4f46e5', '#ec4899'],
        ['#10b981', '#059669'],
        ['#f59e0b', '#d97706'],
        ['#3b82f6', '#8b5cf6'],
        ['#ef4444', '#b91c1c']
    ];
    
    const randomGradient = colors[Math.floor(Math.random() * colors.length)];
    btn.style.background = 'linear-gradient(135deg, ' + randomGradient[0] + ' 0%, ' + randomGradient[1] + ' 100%)';
    btn.style.boxShadow = '0 10px 20px rgba(255, 255, 255, 0.1)';
    
    // Animate logo scale
    const logo = document.querySelector('.logo');
    logo.style.transform = 'scale(1.3) rotate(45deg)';
    setTimeout(() => {
        logo.style.transform = 'scale(1) rotate(0deg)';
    }, 400);
});"""

        repository.insertNode(
            FileNode(name = "script.js", parentId = rootFolderId, isFolder = false, content = jsContent)
        )

        // Select the HTML node by default so there is a stunning interactive screen immediately
        viewModelScope.launch(Dispatchers.Main) {
            _currentFolderId.value = rootFolderId
            val loadedHtmlNode = repository.getNodeById(htmlNodeId)
            selectFile(loadedHtmlNode)
        }
    }

    private fun getBundledHtml(activeFile: FileNode, allNodes: List<FileNode>, activeId: Int, activeContent: String): String {
        var htmlContent = if (activeFile.id == activeId) activeContent else activeFile.content
        val siblings = allNodes.filter { it.parentId == activeFile.parentId && !it.isFolder }

        // Inject CSS
        val cssFiles = siblings.filter { it.name.endsWith(".css", ignoreCase = true) }
        cssFiles.forEach { cssFile ->
            val cssContent = if (cssFile.id == activeId) activeContent else cssFile.content
            val hrefPattern = Regex("""<link\s+[^>]*href=["']${Regex.escape(cssFile.name)}["'][^>]*>""", RegexOption.IGNORE_CASE)
            if (hrefPattern.containsMatchIn(htmlContent)) {
                htmlContent = htmlContent.replace(hrefPattern, "<style>\n$cssContent\n</style>")
            } else {
                if (cssFile.name.lowercase() == "style.css" && !htmlContent.contains("<style>")) {
                    htmlContent = htmlContent.replace("</head>", "<style>\n$cssContent\n</style>\n</head>")
                }
            }
        }

        // Inject JS
        val jsFiles = siblings.filter { it.name.endsWith(".js", ignoreCase = true) }
        jsFiles.forEach { jsFile ->
            val jsContent = if (jsFile.id == activeId) activeContent else jsFile.content
            val srcPattern = Regex("""<script\s+[^>]*src=["']${Regex.escape(jsFile.name)}["'][^>]*>\s*</script>""", RegexOption.IGNORE_CASE)
            if (srcPattern.containsMatchIn(htmlContent)) {
                htmlContent = htmlContent.replace(srcPattern, "<script>\n$jsContent\n</script>")
            } else {
                if (jsFile.name.lowercase() == "script.js" && !htmlContent.contains("<script>")) {
                    htmlContent = htmlContent.replace("</body>", "<script>\n$jsContent\n</script>\n</body>")
                }
            }
        }

        return htmlContent
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
