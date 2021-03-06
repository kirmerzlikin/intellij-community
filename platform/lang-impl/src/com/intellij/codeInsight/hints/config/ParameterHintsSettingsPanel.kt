// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.config

import com.intellij.codeInsight.hints.BlackListDialog
import com.intellij.codeInsight.hints.Option
import com.intellij.lang.Language
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.*
import javax.swing.border.EmptyBorder

class ParameterHintsSettingsPanel(val language: Language,
                                  options: List<Option>,
                                  blackListSupported: Boolean) : JPanel() {
  private val options = mutableListOf<OptionWithCheckBox>()

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    border = JBUI.Borders.empty(5)
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    if (options.isNotEmpty()) {
      add(JLabel("Show hints:"))
      panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
      panel.border = JBUI.Borders.empty(10, 20, 0, 0)
      for (option in options) {
        val checkBox = JCheckBox(option.name, option.get())
        checkBox.border = EmptyBorder(1, 1, 0, 0)
        panel.add(checkBox)
        this.options.add(OptionWithCheckBox(option, checkBox))
      }
    }
    if (blackListSupported) {
      panel.add(Box.createRigidArea(JBUI.size(0, 10)))
      val label = LinkLabel.create("Black list...") {
        BlackListDialog(language).show()
      }
      label.alignmentX = Component.LEFT_ALIGNMENT
      panel.add(label)
    }
    add(panel)
  }

  fun isModified(): Boolean {
    return options.any { it.option.get() != it.checkBox.isSelected }
  }

  fun saveOptions() {
    for ((option, checkBox) in options) {
      if (option.get() != checkBox.isSelected) {
        option.set(checkBox.isSelected)
      }
    }
  }

  private data class OptionWithCheckBox(val option: Option, val checkBox: JCheckBox)
}
