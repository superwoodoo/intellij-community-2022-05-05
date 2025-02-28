// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project

class DaemonFusReporter(private val project: Project) : DaemonCodeAnalyzer.DaemonListener {
  var daemonStartTime = -1L

  override fun daemonStarting(fileEditors: Collection<FileEditor>) {
    daemonStartTime = System.currentTimeMillis()
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    val editor = fileEditors.filterIsInstance<TextEditor>().firstOrNull()?.editor
    val analyzer = (editor?.markupModel as? EditorMarkupModel)?.errorStripeRenderer as? TrafficLightRenderer
    val errorCounts = analyzer?.errorCounts
    val registrar = SeverityRegistrar.getSeverityRegistrar(project)
    val errorIndex = registrar.getSeverityIdx(HighlightSeverity.ERROR)
    val warningIndex = registrar.getSeverityIdx(HighlightSeverity.WARNING)
    val errorCount = errorCounts?.let { it[errorIndex] } ?: -1
    val warningCount = errorCounts?.let { it[warningIndex] } ?: -1
    val elapsedTime = System.currentTimeMillis() - daemonStartTime
    val fileType = editor?.let { FileDocumentManager.getInstance().getFile(it.document)?.fileType }

    DaemonFusCollector.FINISHED.log(
      project,
      EventFields.DurationMs with elapsedTime,
      DaemonFusCollector.ERRORS with errorCount,
      DaemonFusCollector.WARNINGS with warningCount,
      EventFields.FileType with fileType
    )
  }
}

class DaemonFusCollector : CounterUsagesCollector() {
  companion object {
    val GROUP = EventLogGroup("daemon", 1)
    val ERRORS = EventFields.Int("errors")
    val WARNINGS = EventFields.Int("warnings")
    val FINISHED = GROUP.registerVarargEvent("finished",
        EventFields.DurationMs, ERRORS, WARNINGS, EventFields.FileType)
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}
