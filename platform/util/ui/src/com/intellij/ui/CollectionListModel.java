// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Consumer;

public class CollectionListModel<T> extends AbstractListModel<T> implements EditableModel {
  private final List<T> myItems;
  private boolean myListenersMuted = false;

  public CollectionListModel(@NotNull final Collection<? extends T> items) {
    myItems = new ArrayList<>(items);
  }

  @SuppressWarnings("UnusedParameters")
  public CollectionListModel(@NotNull List<T> items, boolean useListAsIs) {
    myItems = items;
  }

  public CollectionListModel(@NotNull List<? extends T> items) {
    myItems = new ArrayList<>(items);
  }

  @SafeVarargs
  public CollectionListModel(T @NotNull ... items) {
    myItems = ContainerUtil.newArrayList(items);
  }

  @NotNull
  protected final List<T> getInternalList() {
    return myItems;
  }

  @Override
  public int getSize() {
    return myItems.size();
  }

  @Override
  public T getElementAt(final int index) {
    return myItems.get(index);
  }

  public void add(final T element) {
    int i = myItems.size();
    myItems.add(element);
    if (!myListenersMuted) {
      fireIntervalAdded(this, i, i);
    }
  }

  public void add(int i,final T element) {
    myItems.add(i, element);
    if (!myListenersMuted) {
      fireIntervalAdded(this, i, i);
    }
  }

  public void add(@NotNull final List<? extends T> elements) {
    addAll(myItems.size(), elements);
  }

  public void addAll(int index, @NotNull final List<? extends T> elements) {
    if (elements.isEmpty()) return;

    myItems.addAll(index, elements);
    if (!myListenersMuted) {
      fireIntervalAdded(this, index, index + elements.size() - 1);
    }
  }

  public void remove(@NotNull T element) {
    int index = getElementIndex(element);
    if (index != -1) {
      remove(index);
    }
  }

  public void setElementAt(@NotNull final T item, final int index) {
    itemReplaced(myItems.set(index, item), item);
    if (!myListenersMuted) {
      fireContentsChanged(this, index, index);
    }
  }

  @SuppressWarnings("UnusedParameters")
  protected void itemReplaced(@NotNull T existingItem, @Nullable T newItem) {
  }

  public void remove(final int index) {
    T item = myItems.remove(index);
    if (item != null) {
      itemReplaced(item, null);
    }
    if (!myListenersMuted) {
      fireIntervalRemoved(this, index, index);
    }
  }

  public void removeAll() {
    int size = myItems.size();
    if (size > 0) {
      myItems.clear();
      if (!myListenersMuted) {
        fireIntervalRemoved(this, 0, size - 1);
      }
    }
  }

  public void contentsChanged(@NotNull final T element) {
    int i = myItems.indexOf(element);
    if (!myListenersMuted) {
      fireContentsChanged(this, i, i);
    }
  }

  public void allContentsChanged() {
    if (!myListenersMuted) {
      fireContentsChanged(this, 0, myItems.size() - 1);
    }
  }

  public void sort(final Comparator<? super T> comparator) {
    myItems.sort(comparator);
  }

  @NotNull
  public List<T> getItems() {
    return Collections.unmodifiableList(myItems);
  }

  public void replaceAll(@NotNull final List<? extends T> elements) {
    removeAll();
    add(elements);
  }

  @Override
  public void addRow() {
  }

  @Override
  public void removeRow(int index) {
    remove(index);
  }

  @Override
  public void exchangeRows(int oldIndex, int newIndex) {
    Collections.swap(myItems, oldIndex, newIndex);
    if (!myListenersMuted) {
      fireContentsChanged(this, oldIndex, oldIndex);
      fireContentsChanged(this, newIndex, newIndex);
    }
  }

  @Override
  public boolean canExchangeRows(int oldIndex, int newIndex) {
    return true;
  }

  @NonNls
  @Override
  public String toString() {
    return getClass().getName() + " (" + getSize() + " elements)";
  }

  public List<T> toList() {
    return new ArrayList<>(myItems);
  }

  public int getElementIndex(T item) {
    return myItems.indexOf(item);
  }

  public boolean isEmpty() {
    return myItems.isEmpty();
  }

  public boolean contains(T item) {
    return getElementIndex(item) >= 0;
  }

  public void removeRange(int fromIndex, int toIndex) {
    if (fromIndex > toIndex) {
      throw new IllegalArgumentException("fromIndex must be <= toIndex");
    }
    for(int i = toIndex; i >= fromIndex; i--) {
      itemReplaced(myItems.remove(i), null);
    }
    if (!myListenersMuted) {
      fireIntervalRemoved(this, fromIndex, toIndex);
    }
  }

  /**
   * Perform batch update of list muting all the listeners when action is in progress. When action finishes,
   * listeners are notified that list content is changed completely.
   *
   * @param action that performs batch update; accepts this model as a parameter
   */
  public void performBatchUpdate(Consumer<CollectionListModel<T>> action) {
    try {
      myListenersMuted = true;
      action.accept(this);
    }
    finally {
      myListenersMuted = false;
    }
    allContentsChanged();
  }
}
