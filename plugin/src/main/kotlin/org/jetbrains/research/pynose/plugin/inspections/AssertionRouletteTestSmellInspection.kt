package org.jetbrains.research.pynose.plugin.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.*
import org.jetbrains.annotations.NotNull
import org.jetbrains.research.pynose.plugin.util.TestSmellBundle
import org.jetbrains.research.pynose.plugin.util.UnittestInspectionsUtils

class AssertionRouletteTestSmellInspection : PyInspection() {
    private val LOG = Logger.getInstance(AssertionRouletteTestSmellInspection::class.java)
    private val assertionCallsInTests: MutableMap<PyFunction, MutableSet<PyCallExpression>> = mutableMapOf()
    private val assertStatementsInTests: MutableMap<PyFunction, MutableSet<PyAssertStatement>> = mutableMapOf()
    private val testHasAssertionRoulette: MutableMap<PyFunction, Boolean> = mutableMapOf()

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        @NotNull session: LocalInspectionToolSession
    ): PyElementVisitor {

        fun registerRoulette(valueParam: PsiElement) {
            holder.registerProblem(
                valueParam,
                TestSmellBundle.message("inspections.roulette.description"),
                ProblemHighlightType.WARNING
            )
        }

        return object : PyInspectionVisitor(holder, session) {
            // todo: issues with highlighting in runIde mode
            override fun visitPyClass(node: PyClass) {
                super.visitPyClass(node)
                if (UnittestInspectionsUtils.isValidUnittestCase(node)) {
                    testHasAssertionRoulette.clear()
                    UnittestInspectionsUtils.gatherUnittestTestMethods(node).forEach { testMethod ->
                        testHasAssertionRoulette[testMethod] = false
                        visitPyElement(testMethod)
                    }

                    for (testMethod in assertionCallsInTests.keys) {
                        val calls: MutableSet<PyCallExpression>? = assertionCallsInTests[testMethod]
                        if (calls!!.size < 2) {
                            continue
                        }
                        for (call in calls) {
                            val argumentList = call.argumentList
                            if (argumentList == null) {
                                LOG.warn("assertion with no argument")
                                continue
                            }
                            if (argumentList.getKeywordArgument("msg") != null) {
                                continue
                            }
                            if (UnittestInspectionsUtils.ASSERT_METHOD_TWO_PARAMS
                                    .contains((call.firstChild as PyReferenceExpression).name) &&
                                argumentList.arguments.size < 3
                            ) {
                                testHasAssertionRoulette.replace(testMethod, true)
                            } else if (UnittestInspectionsUtils.ASSERT_METHOD_ONE_PARAM
                                    .containsKey((call.firstChild as PyReferenceExpression).name) &&
                                argumentList.arguments.size < 2
                            ) {
                                testHasAssertionRoulette.replace(testMethod, true)
                            }
                        }
                    }

                    for (testMethod in assertStatementsInTests.keys) {
                        val asserts: MutableSet<PyAssertStatement>? = assertStatementsInTests[testMethod]
                        if (asserts!!.size < 2) {
                            continue
                        }
                        for (assertStatement in asserts) {
                            val expressions = assertStatement.arguments
                            if (expressions.size < 2) {
                                testHasAssertionRoulette.replace(testMethod, true)
                            }
                        }
                    }

                    for (testMethod in assertStatementsInTests.keys) {
                        if (assertStatementsInTests[testMethod]!!.size == 1 && assertionCallsInTests[testMethod] != null
                            && assertionCallsInTests[testMethod]!!.size == 1
                        ) {
                            testHasAssertionRoulette.replace(testMethod, true)
                        }
                    }
                }
                testHasAssertionRoulette.keys
                    .filter { key -> testHasAssertionRoulette[key]!! }
                    .forEach { pyFunction -> registerRoulette(pyFunction.nameIdentifier!!) }
                testHasAssertionRoulette.clear()
            }

            override fun visitPyCallExpression(callExpression: PyCallExpression) {
                super.visitPyCallExpression(callExpression)
                val child = callExpression.firstChild
                val testMethod = PsiTreeUtil.getParentOfType(callExpression, PyFunction::class.java)
                if (child !is PyReferenceExpression || !UnittestInspectionsUtils.isUnittestCallAssertMethod(child)) {
                    return
                }
                if (assertionCallsInTests[testMethod!!] == null) {
                    assertionCallsInTests[testMethod] = mutableSetOf()
                }
                assertionCallsInTests[testMethod]!!.add(callExpression)
            }

            override fun visitPyAssertStatement(assertStatement: PyAssertStatement) {
                super.visitPyAssertStatement(assertStatement)
                val testMethod = PsiTreeUtil.getParentOfType(assertStatement, PyFunction::class.java)
                if (!UnittestInspectionsUtils.isValidUnittestMethod(testMethod)) {
                    return
                }
                if (assertStatementsInTests[testMethod!!] == null) {
                    assertStatementsInTests[testMethod] = mutableSetOf()
                }
                assertStatementsInTests[testMethod]!!.add(assertStatement)
            }
        }
    }
}