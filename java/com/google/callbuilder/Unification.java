/*
 * Copyright (C) 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.callbuilder;

import com.google.callbuilder.util.Preconditions;
import com.google.callbuilder.util.ValueType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Performs symbolic unification.
 */
final class Unification {
  private Unification() {}

  static interface Unifiable {
    Unifiable apply(Map<Variable, Unifiable> substitutions);
  }

  static final class Atom implements Unifiable {
    @Override
    public Unifiable apply(Map<Variable, Unifiable> substitutions) {
      return this;
    }
  }

  static final class Variable implements Unifiable {
    @Override
    public Unifiable apply(Map<Variable, Unifiable> substitutions) {
      return substitutions.containsKey(this) ? substitutions.get(this) : this;
    }
  }

  static final class Sequence extends ValueType implements Unifiable {
    private final List<Unifiable> items;

    Sequence(List<Unifiable> items) {
      this.items = Collections.unmodifiableList(new ArrayList<Unifiable>(items));
    }

    List<Unifiable> items() {
      return items;
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("items", items);
    }

    @Override
    public Unifiable apply(Map<Variable, Unifiable> substitutions) {
      return sequenceApply(substitutions, items());
    }
  }

  private static Sequence sequenceApply(
      Map<Variable, Unifiable> substitutions, Iterable<Unifiable> items) {
    List<Unifiable> newItems = new ArrayList<>();
    for (Unifiable oldItem : items) {
      newItems.add(oldItem.apply(substitutions));
    }
    return new Sequence(newItems);
  }

  /**
   * Applies s1 to the elements of s2 and adds them into a single list.
   */
  static final Map<Variable, Unifiable> compose(
      Map<Variable, Unifiable> s1, Map<Variable, Unifiable> s2) {
    Map<Variable, Unifiable> composed = new HashMap<>();
    composed.putAll(s1);
    for (Map.Entry<Variable, Unifiable> entry2 : s2.entrySet()) {
      composed.put(entry2.getKey(), entry2.getValue().apply(s1));
    }
    return composed;
  }

  private static @Nullable Substitution sequenceUnify(Sequence lhs, Sequence rhs) {
    if (lhs.items().size() != rhs.items().size()) {
      return null;
    }
    if (lhs.items().isEmpty()) {
      return EMPTY;
    }
    Unifiable firstLhs = lhs.items().get(0);
    Unifiable firstRhs = rhs.items().get(0);
    Substitution subs1 = unify(firstLhs, firstRhs);
    if (subs1 != null) {
      Sequence restLhs = sequenceApply(subs1.resultMap(), lhs.items().subList(1, lhs.items().size()));
      Sequence restRhs = sequenceApply(subs1.resultMap(), rhs.items().subList(1, rhs.items().size()));
      Substitution subs2 = sequenceUnify(restLhs, restRhs);
      if (subs2 != null) {
        Map<Variable, Unifiable> joined = new HashMap<>();
        joined.putAll(subs1.resultMap());
        joined.putAll(subs2.resultMap());
        return new Substitution(joined);
      }
    }
    return null;
  }

  static @Nullable Substitution unify(Unifiable lhs, Unifiable rhs) {
    if (lhs instanceof Variable) {
      return new Substitution(Collections.<Variable, Unifiable>singletonMap((Variable) lhs, rhs));
    }
    if (rhs instanceof Variable) {
      return new Substitution(Collections.<Variable, Unifiable>singletonMap((Variable) rhs, lhs));
    }
    if (lhs instanceof Atom && rhs instanceof Atom) {
      return lhs == rhs ? EMPTY : null;
    }
    if (lhs instanceof Sequence && rhs instanceof Sequence) {
      return sequenceUnify((Sequence) lhs, (Sequence) rhs);
    }
    return null;
  }

  /**
   * The results of a successful unification. This object gives access to the raw variable mapping
   * that resulted from the algorithm, but also supplies functionality for resolving a variable to
   * the fullest extent possible with the {@code resolve} method.
   */
  static final class Substitution extends ValueType {
    private final Map<Variable, Unifiable> resultMap;

    Substitution(Map<Variable, Unifiable> resultMap) {
      this.resultMap = Collections.unmodifiableMap(new HashMap<>(resultMap));
    }

    /**
     * The result of the unification algorithm proper. This does not have everything completely
     * resolved - some variable substitutions are required before getting the most atom-y
     * representation.
     */
    Map<Variable, Unifiable> resultMap() {
      return resultMap;
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("resultMap", resultMap);
    }

    final Unifiable resolve(Unifiable unifiable) {
      Unifiable previous;
      Unifiable current = unifiable;
      do {
        previous = current;
        current = current.apply(resultMap());
      } while (!current.equals(previous));
      return current;
    }
  }

  private static final Substitution EMPTY =
      new Substitution(Collections.<Variable, Unifiable>emptyMap());
}
