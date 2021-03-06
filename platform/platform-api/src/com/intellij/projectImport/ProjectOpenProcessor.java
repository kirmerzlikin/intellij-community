// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.projectImport;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;

public abstract class ProjectOpenProcessor {
  public static final ExtensionPointName<ProjectOpenProcessor> EXTENSION_POINT_NAME =
    new ExtensionPointName<>("com.intellij.projectOpenProcessor");

  @NotNull
  public abstract String getName();

  @Nullable
  public abstract Icon getIcon();

  @Nullable
  public Icon getIcon(@NotNull VirtualFile file) {
    return getIcon();
  }

  public abstract boolean canOpenProject(@NotNull VirtualFile file);

  public boolean isProjectFile(@NotNull VirtualFile file) {
    return canOpenProject(file);
  }

  @Nullable
  public abstract Project doOpenProject(@NotNull VirtualFile virtualFile, @Nullable Project projectToClose, boolean forceOpenInNewFrame);

  /**
   * Allow opening a directory directly if the project files are located in that directory.
   *
   * @return true if project files are searched inside the selected directory, false if the project files must be selected directly.
   */
  public boolean lookForProjectsInDirectory() {
    return true;
  }

  @Nullable
  public static ProjectOpenProcessor getImportProvider(@NotNull VirtualFile file) {
    return getImportProvider(file, false);
  }

  /**
   * @param onlyIfExistingProjectFile when true, doesn't return 'generic' providers that can open any non-project directory/text file
   *                                  (e.g. PlatformProjectOpenProcessor)
   */
  @Nullable
  public static ProjectOpenProcessor getImportProvider(@NotNull VirtualFile file, boolean onlyIfExistingProjectFile) {
    for (ProjectOpenProcessor provider : EXTENSION_POINT_NAME.getIterable()) {
      if (provider.canOpenProject(file) && (!onlyIfExistingProjectFile || provider.isProjectFile(file))) {
        return provider;
      }
    }
    return null;
  }

  /**
   * @return true if this open processor should be ranked over general .idea and .ipr files even if those exist.
   */
  public boolean isStrongProjectInfoHolder() {
    return false;
  }

  public void refreshProjectFiles(@NotNull Path baseDir) {
  }
}
