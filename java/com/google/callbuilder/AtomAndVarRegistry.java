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

import com.google.callbuilder.Unification.Atom;
import com.google.callbuilder.Unification.Sequence;
import com.google.callbuilder.Unification.Unifiable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Maintains a mapping of elements that must be resolved ({@link TypeElement}s and
 * {@link TypeParameterElements}) to objects the {@link Unification} class works with. Can also
 * convert the result of a successful unification into the type string that the variable expresses.
 */
final class AtomAndVarRegistry {
  /**
   * Mapping from Atom objects to the string representation of the item in code to which they
   * correspond.
   */
  private final Map<Atom, String> atomsToCode = new HashMap<>();

  /**
   * The inverse of {@link #atomsToCode}.
   */
  private final Map<String, Atom> codeToAtoms = new HashMap<>();

  String toType(Unifiable resolution) {
    if (resolution instanceof Atom) {
      return atomsToCode.get(resolution);
    }
    Sequence sequence = (Sequence) resolution;
    StringBuilder typeReference = new StringBuilder()
        .append(toType(sequence.items().get(0)));
    if (sequence.items().size() > 1) {
      String prefix = "<";
      for (Unifiable unifiable : sequence.items().subList(1, sequence.items().size())) {
        typeReference.append(prefix);
        prefix = ", ";
        typeReference.append(toType(unifiable));
      }
      typeReference.append(">");
    }
    return typeReference.toString();
  }

  private Atom atom(String codeRepresentation) {
    Atom atom = codeToAtoms.get(codeRepresentation);
    if (atom == null) {
      atom = new Atom();
      atomsToCode.put(atom, codeRepresentation);
      codeToAtoms.put(codeRepresentation, atom);
    }
    return atom;
  }

  Unifiable encode(TypeMirror type, Map<String, ? extends Unifiable> overridenTypeVariables) {
    switch (type.getKind()) {
      case DECLARED:
        List<Unifiable> types = new ArrayList<>();
        DeclaredType declaredType = (DeclaredType) type;
        types.add(atom(CallBuilderProcessor.qualifiedName(declaredType)));
        for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
          types.add(encode(typeArgument, overridenTypeVariables));
        }
        return new Sequence(types);
      case ARRAY:
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case DOUBLE:
      case FLOAT:
      case INT:
      case LONG:
      case SHORT:
      case TYPEVAR:
        String stringRep = type.toString();
        if (overridenTypeVariables.containsKey(stringRep)) {
          return overridenTypeVariables.get(stringRep);
        }
        return atom(stringRep);
      default:
        throw new RuntimeException("type is not supported for use in CallBuilder: " + type);
    }
  }
}
