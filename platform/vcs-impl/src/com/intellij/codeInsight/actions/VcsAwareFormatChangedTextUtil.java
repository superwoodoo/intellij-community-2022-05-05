/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.actions;

import com.intellij.formatting.VcsAwareFormatRangesInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.ex.RangesBuilder;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.FormatRangesInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class VcsAwareFormatChangedTextUtil extends FormatChangedTextUtil {

  @Override
  @NotNull
  public List<TextRange> getChangedTextRanges(@NotNull Project project, @NotNull PsiFile file) throws FilesTooBigForDiffException {
    FormatRangesInfo helper = getChangedTextHelper(file);
    return helper != null ? helper.getRangesToFormat() : ContainerUtil.newArrayList();
  }

  @Override
  @Nullable
  public FormatRangesInfo getChangedTextHelper(@NotNull PsiFile file) throws FilesTooBigForDiffException {
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return null;

    FormatRangesInfo cachedChangedTextHelper = getCachedChangedLines(project, document);
    if (cachedChangedTextHelper != null) {
      return cachedChangedTextHelper;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      CharSequence testContent = file.getUserData(TEST_REVISION_CONTENT);
      if (testContent != null) {
        return calculateContextReformatHelper(document, testContent);
      }
    }

    Change change = ChangeListManager.getInstance(project).getChange(file.getVirtualFile());
    if (change == null) {
      return null;
    }
    if (change.getType() == Change.Type.NEW) {
      return new VcsAwareFormatRangesInfo(file.getTextRange());
    }

    String contentFromVcs = getRevisionedContentFrom(change);
    return contentFromVcs != null ? calculateContextReformatHelper(document, contentFromVcs) : null;
  }

  @Nullable
  private static String getRevisionedContentFrom(@NotNull Change change) {
    ContentRevision revision = change.getBeforeRevision();
    if (revision == null) {
      return null;
    }

    try {
      return revision.getContent();
    }
    catch (VcsException e) {
      LOG.error("Can't get content for: " + change.getVirtualFile(), e);
      return null;
    }
  }

  @Nullable
  private static FormatRangesInfo getCachedChangedLines(@NotNull Project project, @NotNull Document document) {
    LineStatusTracker tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document);
    if (tracker != null) {
      List<Range> ranges = tracker.getRanges();
      if (ranges != null) {
        return getChangedTextRanges(document, ranges);
      }
    }
    return null;
  }

  @NotNull
  protected static FormatRangesInfo calculateContextReformatHelper(@NotNull Document document,
                                                                   @NotNull CharSequence contentFromVcs) throws FilesTooBigForDiffException
  {
    return getChangedTextRanges(document, getRanges(document, contentFromVcs));
  }

  @NotNull
  private static List<Range> getRanges(@NotNull Document document,
                                       @NotNull CharSequence contentFromVcs) throws FilesTooBigForDiffException
  {
    Document documentFromVcs = ((EditorFactoryImpl)EditorFactory.getInstance()).createDocument(contentFromVcs, true, false);
    return RangesBuilder.createRanges(document, documentFromVcs);
  }

  @Override
  public int calculateChangedLinesNumber(@NotNull Document document, @NotNull CharSequence contentFromVcs) {
    try {
      List<Range> changedRanges = getRanges(document, contentFromVcs);
      int linesChanges = 0;
      for (Range range : changedRanges) {
        linesChanges += countLines(range);
      }
      return linesChanges;
    } catch (FilesTooBigForDiffException e) {
      LOG.info("File too big, can not calculate changed lines number");
      return -1;
    }
  }

  private static int countLines(Range range) {
    byte rangeType = range.getType();
    if (rangeType == Range.MODIFIED) {
      int currentChangedLines = range.getLine2() - range.getLine1();
      int revisionLinesChanged = range.getVcsLine2() - range.getVcsLine1();
      return Math.max(currentChangedLines, revisionLinesChanged);
    }
    else if (rangeType == Range.DELETED) {
      return range.getVcsLine2() - range.getVcsLine1();
    }
    else if (rangeType == Range.INSERTED) {
      return range.getLine2() - range.getLine1();
    }

    return 0;
  }

  @NotNull
  private static FormatRangesInfo getChangedTextRanges(@NotNull Document document, @NotNull List<Range> changedRanges) {
    List<TextRange> ranges = ContainerUtil.newArrayList();
    List<TextRange> insertedRanges = ContainerUtil.newArrayList();
    
    for (Range range : changedRanges) {
      if (range.getType() != Range.DELETED) {
        int changeStartLine = range.getLine1();
        int changeEndLine = range.getLine2();

        int lineStartOffset = document.getLineStartOffset(changeStartLine);
        int lineEndOffset = document.getLineEndOffset(changeEndLine - 1);

        TextRange changedTextRange = new TextRange(lineStartOffset, lineEndOffset);
        ranges.add(changedTextRange);
        if (range.getType() == Range.INSERTED) {
          insertedRanges.add(changedTextRange);
        }
      }
    }
    return new VcsAwareFormatRangesInfo(ranges, insertedRanges);
  }

  @Override
  public boolean isChangeNotTrackedForFile(@NotNull Project project, @NotNull PsiFile file) {
    boolean isUnderVcs = VcsUtil.isFileUnderVcs(project, VcsUtil.getFilePath(file.getVirtualFile()));
    if (!isUnderVcs) return true;

    ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(project);
    List<VirtualFile> unversionedFiles = changeListManager.getUnversionedFiles();
    if (unversionedFiles.contains(file.getVirtualFile())) {
      return true;
    }

    return false;
  }
  
}
