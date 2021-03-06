// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.ide.ui.search.ConfigurableHit;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LightColors;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.DocumentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Set;

public abstract class SettingsFilter extends ElementFilter.Active.Impl<SimpleNode> {
  final OptionsEditorContext myContext = new OptionsEditorContext();
  private final Project myProject;

  private final SearchTextField mySearch;
  private final List<? extends ConfigurableGroup> myGroups;

  private final SearchableOptionsRegistrar myRegistrar = SearchableOptionsRegistrar.getInstance();
  private Set<Configurable> myFiltered;
  private ConfigurableHit myHits;

  private boolean myUpdateRejected;
  private Configurable myLastSelected;

  SettingsFilter(Project project, List<? extends ConfigurableGroup> groups, SearchTextField search) {
    myProject = project;
    myGroups = groups;
    mySearch = search;
    mySearch.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent event) {
        update(event.getType(), true, false);
        // request focus if needed on changing the filter text
        IdeFocusManager manager = IdeFocusManager.findInstanceByComponent(mySearch);
        if (manager.getFocusedDescendantFor(mySearch) == null) {
          manager.requestFocus(mySearch, true);
        }
      }
    });
    mySearch.getTextEditor().addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        if (!mySearch.getText().isEmpty()) {
          if (!myContext.isHoldingFilter()) {
            setHoldingFilter(true);
          }
          if (!mySearch.getTextEditor().isFocusOwner()) {
            mySearch.selectText();
          }
        }
      }
    });
  }

  abstract Configurable getConfigurable(SimpleNode node);

  abstract SimpleNode findNode(Configurable configurable);

  abstract void updateSpotlight(boolean now);

  @Override
  public boolean shouldBeShowing(SimpleNode node) {
    if (myFiltered != null) {
      Configurable configurable = getConfigurable(node);
      if (configurable != null) {
        if (!myFiltered.contains(configurable)) {
          if (myHits != null) {
            Set<Configurable> configurables = myHits.getNameFullHits();
            while (node != null) {
              if (configurable != null) {
                if (configurables.contains(configurable)) {
                  return true;
                }
              }
              node = node.getParent();
              configurable = getConfigurable(node);
            }
          }
          return false;
        }
      }
    }
    return true;
  }

  String getFilterText() {
    String text = mySearch.getText();
    if (text != null) {
      text = text.trim();
      if (1 < text.length()) {
        return text;
      }
    }
    return "";
  }

  private void setHoldingFilter(boolean holding) {
    myContext.setHoldingFilter(holding);
    updateSpotlight(false);
  }

  boolean contains(@NotNull Configurable configurable) {
    return myHits != null && myHits.getNameHits().contains(configurable);
  }

  void update(String text, boolean adjustSelection, boolean now) {
    try {
      myUpdateRejected = true;
      mySearch.setText(text);
    }
    finally {
      myUpdateRejected = false;
    }
    update(DocumentEvent.EventType.CHANGE, adjustSelection, now);
  }

  private void update(@NotNull DocumentEvent.EventType type, boolean adjustSelection, boolean now) {
    if (myUpdateRejected) {
      return;
    }
    String text = getFilterText();
    if (text.isEmpty()) {
      myContext.setHoldingFilter(false);
      myFiltered = null;
    }
    else {
      myContext.setHoldingFilter(true);
      myHits = myRegistrar.getConfigurables(myGroups, type, myFiltered, text, myProject);
      myFiltered = myHits.getAll();
    }
    mySearch.getTextEditor().setBackground(myFiltered != null && myFiltered.isEmpty()
                                           ? LightColors.RED
                                           : UIUtil.getTextFieldBackground());


    Configurable current = myContext.getCurrentConfigurable();

    boolean shouldMoveSelection = myHits == null || !myHits.getNameFullHits().contains(current) &&
                                                    !myHits.getContentHits().contains(current);

    if (shouldMoveSelection && type != DocumentEvent.EventType.INSERT && (myFiltered == null || myFiltered.contains(current))) {
      shouldMoveSelection = false;
    }

    Configurable candidate = adjustSelection ? current : null;
    if (shouldMoveSelection && myHits != null) {
      if (!myHits.getNameHits().isEmpty()) {
        candidate = findConfigurable(myHits.getNameHits(), myHits.getNameFullHits());
      }
      else if (!myHits.getContentHits().isEmpty()) {
        candidate = findConfigurable(myHits.getContentHits(), null);
      }
    }
    updateSpotlight(false);

    if ((myFiltered == null || !myFiltered.isEmpty()) && candidate == null && myLastSelected != null) {
      candidate = myLastSelected;
      myLastSelected = null;
    }
    if (candidate == null && current != null) {
      myLastSelected = current;
    }
    SimpleNode node = !adjustSelection ? null : findNode(candidate);
    fireUpdate(node, adjustSelection, now);
  }

  private static Configurable findConfigurable(Set<? extends Configurable> configurables, Set<? extends Configurable> hits) {
    Configurable candidate = null;
    for (Configurable configurable : configurables) {
      if (hits != null && hits.contains(configurable)) {
        return configurable;
      }
      if (candidate == null && !isEmptyParent(configurable)) {
        candidate = configurable;
      }
    }
    return candidate;
  }

  private static boolean isEmptyParent(Configurable configurable) {
    SearchableConfigurable.Parent parent = ConfigurableWrapper.cast(SearchableConfigurable.Parent.class, configurable);
    return parent != null && !parent.hasOwnContent();
  }
}
