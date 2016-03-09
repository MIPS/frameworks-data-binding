/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.databinding.tool.expr;

import android.databinding.tool.reflection.ModelAnalyzer;
import android.databinding.tool.reflection.ModelClass;
import android.databinding.tool.util.Preconditions;
import android.databinding.tool.writer.KCode;

import java.util.List;

public class MathExpr extends Expr {
    final String mOp;

    MathExpr(Expr left, String op, Expr right) {
        super(left, right);
        mOp = op;
    }

    @Override
    protected String computeUniqueKey() {
        return join(getLeft().getUniqueKey(), mOp, getRight().getUniqueKey());
    }

    @Override
    protected ModelClass resolveType(ModelAnalyzer modelAnalyzer) {
        if ("+".equals(mOp)) {
            // TODO we need upper casting etc.
            if (getLeft().getResolvedType().isString()
                    || getRight().getResolvedType().isString()) {
                return modelAnalyzer.findClass(String.class);
            }
        }
        return modelAnalyzer.findCommonParentOf(getLeft().getResolvedType(),
                getRight().getResolvedType());
    }

    @Override
    protected List<Dependency> constructDependencies() {
        return constructDynamicChildrenDependencies();
    }

    public Expr getLeft() {
        return getChildren().get(0);
    }

    public Expr getRight() {
        return getChildren().get(1);
    }

    @Override
    protected KCode generateCode() {
        return new KCode().app("(", getLeft().toCode())
                .app(") ")
                .app(mOp)
                .app(" (", getRight().toCode())
                .app(")");
    }

    @Override
    public String getInvertibleError() {
        if (mOp.equals("%")) {
            return "The modulus operator (%) is not supported in two-way binding.";
        } else if (getResolvedType().isString()) {
            return "String concatenation operator (+) is not supported in two-way binding.";
        }
        if (!getLeft().isDynamic()) {
            return getRight().getInvertibleError();
        } else if (!getRight().isDynamic()) {
            return getLeft().getInvertibleError();
        } else {
            return "Arithmetic operator " + mOp + " is not supported with two dynamic expressions.";
        }
    }

    @Override
    public Expr generateInverse(ExprModel model, Expr value, String bindingClassName) {
        final Expr left = getLeft();
        final Expr right = getRight();
        Preconditions.check(left.isDynamic() ^ right.isDynamic(), "Two-way binding of a math " +
                "operations requires A signle dynamic expression. Neither or both sides are " +
                "dynamic: (%s) %s (%s)", left, mOp, right);
        final Expr constExpr = (left.isDynamic() ? right : left).cloneToModel(model);
        final Expr newValue;
        switch (mOp.charAt(0)) {
            case '+': // const + x = value  => x = value - const
                newValue = model.math(value, "-", constExpr);
                break;
            case '*': // const * x = value => x = value / const
                newValue = model.math(value, "/", constExpr);
                break;
            case '-':
                if (!left.isDynamic()) { // const - x = value => x = const - (value)
                    newValue = model.math(constExpr, "-", value);
                } else { // x - const = value => x = value + const)
                    newValue = model.math(value, "+", constExpr);
                }
                break;
            case '/':
                if (!left.isDynamic()) { // const / x = value => x = const / value
                    newValue = model.math(constExpr, "/", value);
                } else { // x / const = value => x = value * const
                    newValue = model.math(value, "*", constExpr);
                }
                break;
            default:
                throw new IllegalStateException("Invalid math operation is not invertible: " + mOp);
        }
        final Expr varExpr = left.isDynamic() ? left : right;
        return varExpr.generateInverse(model, newValue, bindingClassName);
    }

    @Override
    public Expr cloneToModel(ExprModel model) {
        return model.math(getLeft().cloneToModel(model), mOp, getRight().cloneToModel(model));
    }

    @Override
    public String toString() {
        return "(" + getLeft() + ") " + mOp + " (" + getRight() + ")";
    }
}
