// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.ToolbarHolder
import com.intellij.openapi.wm.impl.customFrameDecorations.header.FrameHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MainFrameCustomHeader
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.openapi.wm.impl.headertoolbar.isToolbarInHeader
import com.intellij.ui.IconManager
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations
import com.jetbrains.CustomWindowDecoration.MENU_BAR
import java.awt.*
import java.awt.GridBagConstraints.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.math.roundToInt

private enum class ShowMode {
  MENU, TOOLBAR
}

internal class ToolbarFrameHeader(frame: JFrame, ideMenu: IdeMenuBar) : FrameHeader(frame), UISettingsListener, ToolbarHolder, MainFrameCustomHeader {
  private val myMenuBar = ideMenu
  private val menuAction = ShowMenuAction()
  private val myMenuButton = createMenuButton(menuAction)
  private var myToolbar : MainToolbar? = null
  private val myToolbarPlaceholder = NonOpaquePanel()
  private val myHeaderContent = createHeaderContent()
  private val menuShortcutHandler = MainMenuMnemonicHandler(frame, menuAction)

  private val contentResizeListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      updateCustomDecorationHitTestSpots()
    }
  }

  private var mode = ShowMode.MENU

  init {
    layout = GridBagLayout()
    val gb = GridBag().anchor(WEST)

    updateLayout(UISettings.getInstance())

    productIcon.border = JBUI.Borders.empty(V, 0, V, 0)
    add(productIcon, gb.nextLine().next().anchor(WEST).insetLeft(H))
    add(myHeaderContent, gb.next().fillCell().anchor(CENTER).weightx(1.0).weighty(1.0))
    val buttonsView = buttonPanes.getView()
    if (SystemInfo.isWindows) buttonsView.border = JBUI.Borders.emptyLeft(8)
    add(buttonsView, gb.next().anchor(EAST))

    setCustomFrameTopBorder({ false }, {true})

    Disposer.register(this, menuShortcutHandler)
  }

  override fun updateToolbar() {
    removeToolbar()

    val toolbar = MainToolbar()
    toolbar.init((frame as? IdeFrame)?.project)
    toolbar.isOpaque = false
    toolbar.addComponentListener(contentResizeListener)
    myToolbar = toolbar

    myToolbarPlaceholder.add(myToolbar)
    myToolbarPlaceholder.revalidate()
  }

  override fun removeToolbar() {
    myToolbar?.let { it.removeComponentListener(contentResizeListener) }
    myToolbarPlaceholder.removeAll()
    myToolbarPlaceholder.revalidate()
  }

  override fun installListeners() {
    super.installListeners()
    menuShortcutHandler.registerShortcuts()
    myMenuBar.addComponentListener(contentResizeListener)
  }

  override fun uninstallListeners() {
    super.uninstallListeners()
    menuShortcutHandler.unregisterShortcuts()
    myMenuBar.removeComponentListener(contentResizeListener)
    myToolbar?.let { it.removeComponentListener(contentResizeListener) }
  }

  override fun updateMenuActions(forceRebuild: Boolean) {} //todo remove

  override fun getComponent(): JComponent = this

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateLayout(uiSettings)
    when (mode) {
      ShowMode.TOOLBAR -> updateToolbar()
      ShowMode.MENU -> removeToolbar()
    }
  }

  override fun getHitTestSpots(): List<Pair<RelativeRectangle, Int>> {
    val result = super.getHitTestSpots().toMutableList()

    when (mode) {
      ShowMode.MENU -> {
        result.add(Pair(getElementRect(myMenuBar) { rect ->
          val state = frame.extendedState
          if (state != Frame.MAXIMIZED_VERT && state != Frame.MAXIMIZED_BOTH) {
            val topGap = (rect.height / 3).toFloat().roundToInt()
            rect.y += topGap
            rect.height -= topGap
          }
        }, MENU_BAR))
      }
      ShowMode.TOOLBAR -> {
        result.add(Pair(getElementRect(myMenuButton), MENU_BAR))
        myToolbar?.components?.filter { it.isVisible }?.forEach { result.add(Pair(getElementRect(it), MENU_BAR)) }
      }
    }

    return result
  }

  override fun getHeaderBackground(active: Boolean) = CustomFrameDecorations.mainToolbarBackground(active)

  private fun getElementRect(comp: Component, rectProcessor: ((Rectangle) -> Unit)? = null): RelativeRectangle {
    val rect = Rectangle(comp.size)
    rectProcessor?.invoke(rect)
    return RelativeRectangle(comp, rect)
  }

  fun createHeaderContent(): JPanel {
    val res = NonOpaquePanel(CardLayout())
    res.border = JBUI.Borders.empty()

    val menuPnl = NonOpaquePanel(GridBagLayout()).apply {
      val gb = GridBag().anchor(WEST).nextLine()
      add(myMenuBar, gb.next().insetLeft(JBUI.scale(20)))
      add(Box.createHorizontalGlue(), gb.next().weightx(1.0).fillCell())
    }
    val toolbarPnl = NonOpaquePanel(GridBagLayout()).apply {
      val gb = GridBag().anchor(WEST).nextLine()
      add(myMenuButton, gb.next().insetLeft(JBUI.scale(20)))
      add(myToolbarPlaceholder, gb.next().weightx(1.0).fillCellHorizontally().insetLeft(JBUI.scale(16)))
    }

    res.add(ShowMode.MENU.name, menuPnl)
    res.add(ShowMode.TOOLBAR.name, toolbarPnl)

    return res
  }

  private fun updateLayout(settings: UISettings) {
    mode = if (isToolbarInHeader(settings)) ShowMode.TOOLBAR else ShowMode.MENU
    val layout = myHeaderContent.layout as CardLayout
    layout.show(myHeaderContent, mode.name)
  }

  private fun createMenuButton(action: AnAction): JComponent {
    return ActionButton(action, PresentationFactory().getPresentation(action),
                        ActionPlaces.MAIN_MENU_IN_POPUP, Dimension(40, 40))
      .apply { setLook(HeaderToolbarButtonLook()) }
  }

  private inner class ShowMenuAction : DumbAwareAction() {

    private val icon = IconManager.getInstance().getIcon("expui/general/windowsMenu@20x20.svg", AllIcons::class.java)

    override fun update(e: AnActionEvent) {
      e.presentation.icon = icon
      e.presentation.text = IdeBundle.message("main.toolbar.menu.button")
    }

    override fun actionPerformed(e: AnActionEvent) = createPopup(e.dataContext).showUnderneathOf(myMenuButton)

    private fun createPopup(context: DataContext): JBPopup {
      val mainMenu = ActionManager.getInstance().getAction(IdeActions.GROUP_MAIN_MENU) as ActionGroup
      return JBPopupFactory.getInstance()
        .createActionGroupPopup(null, mainMenu, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true, ActionPlaces.MAIN_MENU_IN_POPUP)
        .apply { setShowSubmenuOnHover(true) }
        .apply { setMinimumSize(Dimension(CustomFrameDecorations.menuPopupMinWidth(), 0)) }
    }
  }

}

private class MainMenuMnemonicHandler(val frame: JFrame, val action: AnAction) : Disposable {

  private var disposable: Disposable? = null

  fun registerShortcuts() {
    if (disposable == null) disposable = Disposer.newDisposable()

    val shortcutSet = ActionUtil.getShortcutSet("MainMenuButton.ShowMenu")
    action.registerCustomShortcutSet(shortcutSet, frame.rootPane, disposable)
  }

  fun unregisterShortcuts() {
    disposable?.let { Disposer.dispose(it) }
  }

  override fun dispose() = unregisterShortcuts()
}



