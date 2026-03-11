package org.jetbrains.plugins.template.editor

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.treeStructure.Tree
import java.awt.Component
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.TreePath

class ExploreModelPanel(json: String, onPathSelected: ((String) -> Unit)? = null) : JPanel(BorderLayout()) {
    private enum class PathFormat { DOT, BRACKET }

    private data class TreeNodeMeta(
        val display: String,
        val pathSegment: String,
        val node: JsonNode?,
    ) {
        override fun toString(): String = display
    }

    private val objectMapper = ObjectMapper()
    private val pathField = JTextField()
    private val searchField = JTextField()
    private val pathFormatSelector = ComboBox(arrayOf("Dot", "Bracket"))
    private val expandButton = JButton("Expand All")
    private val collapseButton = JButton("Collapse All")
    private val copyButton = JButton("Copy Path")
    private val statusLabel = JLabel("Ready")
    private val inspectorArea = JTextArea()
    private val tree = Tree(DefaultMutableTreeNode("Loading..."))

    private var rootNode: JsonNode? = null
    private var selectedPath: String? = null
    private var selectedTreePath: TreePath? = null
    private var selectedSegments: List<String> = emptyList()
    private var pathFormat: PathFormat = PathFormat.DOT
    private var activeFilter: String? = null
    private var matchedNodesCount: Int = 0

    init {
        rootNode = try {
            objectMapper.readTree(json)
        } catch (_: OutOfMemoryError) {
            JOptionPane.showMessageDialog(this, "File too large to parse. Out of memory.", "Error", JOptionPane.ERROR_MESSAGE)
            null
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Failed to parse JSON: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
            null
        }

        val controls = JPanel()
        controls.layout = BoxLayout(controls, BoxLayout.X_AXIS)
        searchField.toolTipText = "Filter keys and values"
        pathField.isEditable = false
        pathField.toolTipText = "Selected JSON path"
        controls.add(JLabel("Search: "))
        controls.add(searchField)
        controls.add(JLabel("  Path format: "))
        controls.add(pathFormatSelector)
        controls.add(expandButton)
        controls.add(collapseButton)
        controls.add(JLabel("Path: "))
        controls.add(pathField)
        controls.add(copyButton)
        controls.add(JLabel("  "))
        controls.add(statusLabel)

        inspectorArea.isEditable = false
        inspectorArea.lineWrap = true
        inspectorArea.wrapStyleWord = true
        inspectorArea.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        inspectorArea.text = "Select a node to inspect details."

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(tree), JScrollPane(inspectorArea))
        splitPane.resizeWeight = 0.7
        splitPane.minimumSize = Dimension(300, 200)

        add(controls, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)

        tree.cellRenderer = HighlightTreeCellRenderer()
        rebuildTree(null)
        installShortcuts()

        tree.addTreeSelectionListener { e ->
            val path = e.path
            selectedTreePath = path
            selectedSegments = extractSegments(path)
            selectedPath = computeJsonPath(path, pathFormat)
            pathField.text = selectedPath.orEmpty()
            onPathSelected?.invoke(pathField.text)
            updateInspector(path)
        }

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = refreshFilter()
            override fun removeUpdate(e: DocumentEvent?) = refreshFilter()
            override fun changedUpdate(e: DocumentEvent?) = refreshFilter()

            private fun refreshFilter() {
                rebuildTree(searchField.text.trim().ifBlank { null })
            }
        })

        pathFormatSelector.addActionListener {
            pathFormat = if (pathFormatSelector.selectedIndex == 1) PathFormat.BRACKET else PathFormat.DOT
            selectedPath = selectedTreePath?.let { computeJsonPath(it, pathFormat) }
            pathField.text = selectedPath.orEmpty()
        }

        expandButton.addActionListener { expandAll() }
        collapseButton.addActionListener { collapseAll() }

        copyButton.addActionListener {
            copySelectedPathToClipboard()
        }
    }

    private fun rebuildTree(filter: String?) {
        val previousSegments = selectedSegments
        activeFilter = filter
        matchedNodesCount = 0
        val node = rootNode
        val treeRoot = if (node == null) {
            DefaultMutableTreeNode("Invalid JSON")
        } else {
            createTreeNode("root", "", node, filter)
                ?: DefaultMutableTreeNode("No matches for \"${filter.orEmpty()}\"")
        }
        tree.model = javax.swing.tree.DefaultTreeModel(treeRoot)
        selectedTreePath = null
        selectedSegments = emptyList()
        selectedPath = null
        pathField.text = ""
        inspectorArea.text = "Select a node to inspect details."
        updateStatus(treeRoot, filter)
        if (filter.isNullOrBlank()) {
            expandTopLevels()
        } else {
            expandAll()
        }
        if (previousSegments.isNotEmpty()) {
            restoreSelection(previousSegments)
        }
    }

    private fun extractSegments(path: TreePath): List<String> {
        val segments = mutableListOf<String>()
        for (i in 1 until path.pathCount) {
            val meta = (path.getPathComponent(i) as? DefaultMutableTreeNode)?.userObject as? TreeNodeMeta ?: continue
            segments.add(meta.pathSegment)
        }
        return segments
    }

    private fun restoreSelection(segments: List<String>) {
        val root = tree.model.root as? DefaultMutableTreeNode ?: return
        var current = root
        val components = mutableListOf<Any>(root)
        for (segment in segments) {
            val next = (0 until current.childCount)
                .mapNotNull { current.getChildAt(it) as? DefaultMutableTreeNode }
                .firstOrNull {
                    val meta = it.userObject as? TreeNodeMeta
                    meta?.pathSegment == segment
                } ?: return
            components.add(next)
            current = next
        }
        val restoredPath = TreePath(components.toTypedArray())
        tree.selectionPath = restoredPath
        tree.scrollPathToVisible(restoredPath)
    }

    private fun createTreeNode(name: String, segment: String, node: JsonNode, filter: String?): DefaultMutableTreeNode? {
        val display = buildDisplayLabel(name, node)
        val treeNode = DefaultMutableTreeNode(TreeNodeMeta(display, segment, node))
        var hasChildMatch = false

        when {
            node.isObject -> node.fields().forEach { (key, value) ->
                val child = createTreeNode(key, key, value, filter)
                if (child != null) {
                    treeNode.add(child)
                    hasChildMatch = true
                }
            }
            node.isArray -> node.forEachIndexed { idx, value ->
                val child = createTreeNode("[$idx]", "[$idx]", value, filter)
                if (child != null) {
                    treeNode.add(child)
                    hasChildMatch = true
                }
            }
            else -> {
                hasChildMatch = false
            }
        }

        if (filter.isNullOrBlank()) {
            return treeNode
        }
        val selfMatch = matchesFilter(name, node, filter)
        if (selfMatch) {
            matchedNodesCount++
        }
        return if (selfMatch || hasChildMatch) treeNode else null
    }

    private fun matchesFilter(name: String, node: JsonNode, filter: String): Boolean {
        val query = filter.lowercase()
        if (name.lowercase().contains(query)) {
            return true
        }
        if (node.isValueNode) {
            return node.asText().lowercase().contains(query)
        }
        return false
    }

    private fun buildDisplayLabel(name: String, node: JsonNode): String {
        return when {
            node.isObject -> "$name: {object}"
            node.isArray -> "$name: [array:${node.size()}]"
            node.isTextual -> "$name: \"${truncate(node.asText())}\""
            node.isNull -> "$name: null"
            node.isBoolean || node.isNumber -> "$name: ${node.asText()}"
            else -> "$name: ${truncate(node.toString())}"
        }
    }

    private fun truncate(value: String, max: Int = 60): String {
        return if (value.length <= max) value else value.take(max) + "..."
    }

    private fun updateStatus(treeRoot: DefaultMutableTreeNode, filter: String?) {
        statusLabel.text = when {
            filter.isNullOrBlank() -> "Showing full model"
            treeRoot.userObject == "No matches for \"${filter}\"" -> "No matches"
            else -> "$matchedNodesCount match(es)"
        }
    }

    private fun computeJsonPath(path: TreePath, format: PathFormat): String {
        val sb = StringBuilder()
        for (i in 1 until path.pathCount) { // skip root
            val userObject = (path.getPathComponent(i) as? DefaultMutableTreeNode)?.userObject as? TreeNodeMeta ?: continue
            val segment = userObject.pathSegment
            when (format) {
                PathFormat.DOT -> {
                    if (segment.startsWith("[")) {
                        sb.append(segment)
                    } else {
                        if (sb.isNotEmpty()) {
                            sb.append('.')
                        }
                        sb.append(segment)
                    }
                }
                PathFormat.BRACKET -> {
                    if (segment.startsWith("[")) {
                        sb.append(segment)
                    } else {
                        sb.append("[\"").append(segment.replace("\"", "\\\"")).append("\"]")
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun updateInspector(path: TreePath) {
        val nodeMeta = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? TreeNodeMeta
        val node = nodeMeta?.node ?: run {
            inspectorArea.text = "No node details available."
            return
        }
        val type = when {
            node.isObject -> "Object"
            node.isArray -> "Array"
            node.isTextual -> "String"
            node.isNumber -> "Number"
            node.isBoolean -> "Boolean"
            node.isNull -> "Null"
            else -> "Value"
        }
        val valuePreview = when {
            node.isObject || node.isArray -> objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
            else -> node.asText()
        }
        val currentPath = computeJsonPath(path, pathFormat)
        inspectorArea.text = buildString {
            appendLine("Type: $type")
            appendLine("Path: $currentPath")
            appendLine("Length: ${valuePreview.length}")
            appendLine()
            append("Value Preview:\n${truncate(valuePreview, 4000)}")
        }
    }

    private fun expandAll() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    private fun collapseAll() {
        for (row in tree.rowCount - 1 downTo 1) {
            tree.collapseRow(row)
        }
    }

    private fun expandTopLevels() {
        tree.expandRow(0)
        val root = tree.model.root as? DefaultMutableTreeNode ?: return
        for (i in 0 until root.childCount) {
            tree.expandRow(i + 1)
        }
    }

    private fun installShortcuts() {
        val menuMask = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        val shortcutScope = WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        val focusSearchKey = KeyStroke.getKeyStroke(KeyEvent.VK_F, menuMask)
        val copyPathKey = KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask)
        val clearSearchKey = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)

        val focusedInputMap = getInputMap(shortcutScope)
        focusedInputMap.put(focusSearchKey, "focusSearch")
        actionMap.put("focusSearch", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                searchField.requestFocusInWindow()
                searchField.selectAll()
            }
        })

        focusedInputMap.put(copyPathKey, "copyCurrentPath")
        actionMap.put("copyCurrentPath", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                copySelectedPathToClipboard()
            }
        })

        val searchInputMap = searchField.getInputMap(WHEN_FOCUSED)
        searchInputMap.put(clearSearchKey, "clearSearch")
        searchField.actionMap.put("clearSearch", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                clearSearchAndResetTree()
            }
        })
    }

    private fun copySelectedPathToClipboard() {
        selectedPath?.let {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(it), null)
            setTransientStatus("Path copied")
        } ?: run {
            setTransientStatus("No path selected")
        }
    }

    private fun clearSearchAndResetTree() {
        searchField.text = ""
        rebuildTree(null)
        tree.requestFocusInWindow()
        setTransientStatus("Search cleared")
    }

    private fun setTransientStatus(message: String, durationMs: Int = 1600) {
        statusLabel.text = message
        Timer(durationMs) {
            updateStatus(tree.model.root as? DefaultMutableTreeNode ?: DefaultMutableTreeNode(""), activeFilter)
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun highlightText(text: String, query: String?): String {
        val escaped = escapeHtml(text)
        if (query.isNullOrBlank()) {
            return escaped
        }
        val lower = escaped.lowercase()
        val needle = escapeHtml(query).lowercase()
        if (needle.isBlank()) {
            return escaped
        }
        var index = lower.indexOf(needle)
        if (index < 0) {
            return escaped
        }
        val out = StringBuilder()
        var cursor = 0
        while (index >= 0) {
            out.append(escaped, cursor, index)
            out.append("<span style='background:#ffe082;'>")
            out.append(escaped, index, index + needle.length)
            out.append("</span>")
            cursor = index + needle.length
            index = lower.indexOf(needle, cursor)
        }
        out.append(escaped.substring(cursor))
        return out.toString()
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private inner class HighlightTreeCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: javax.swing.JTree?,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ): Component {
            val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus) as JLabel
            val node = value as? DefaultMutableTreeNode
            val meta = node?.userObject as? TreeNodeMeta
            if (meta != null) {
                component.text = "<html>${highlightText(meta.display, activeFilter)}</html>"
            }
            return component
        }
    }
}
