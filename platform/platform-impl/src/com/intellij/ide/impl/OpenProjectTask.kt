// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.openapi.project.Project

// we cannot rewrite ProjectUtil in Kotlin (in this case such class will be not required) because not all like Kotlin
data class OpenProjectTask(val forceOpenInNewFrame: Boolean = false,
                           val projectToClose: Project? = null) {
  var checkDirectoryForFileBasedProjects: Boolean = true
}