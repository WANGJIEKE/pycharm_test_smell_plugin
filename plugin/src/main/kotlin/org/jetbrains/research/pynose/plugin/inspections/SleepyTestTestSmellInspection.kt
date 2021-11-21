package org.jetbrains.research.pynose.plugin.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.pyi.PyiFile
import org.jetbrains.research.pynose.plugin.util.GeneralInspectionsUtils
import org.jetbrains.research.pynose.plugin.util.TestSmellBundle

class SleepyTestTestSmellInspection : PyInspection() {
    private val LOG = Logger.getInstance(SleepyTestTestSmellInspection::class.java)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {

        fun registerSleepy(valueParam: PsiElement) {
            holder.registerProblem(
                valueParam,
                TestSmellBundle.message("inspections.sleepy.description"),
                ProblemHighlightType.WARNING
            )
        }

        return object : PyInspectionVisitor(holder, session) {
            override fun visitPyCallExpression(callExpression: PyCallExpression) {
                super.visitPyCallExpression(callExpression)
                if (!GeneralInspectionsUtils.redirectValidParentCheck(callExpression)) {
                    return
                }
                if (callExpression.callee !is PyReferenceExpression) {
                    super.visitPyElement(callExpression)
                    return
                }
                val callExprRef = callExpression.callee as? PyReferenceExpression ?: return
                val element = callExprRef.followAssignmentsChain(PyResolveContext.defaultContext()).element ?: return
                if (element !is PyFunction || element.name != "sleep") {
                    super.visitPyElement(callExpression)
                    return
                }
                if (element.parent !is PyiFile || (element.parent as PyiFile).name != "time.pyi") {
                    super.visitPyElement(callExpression)
                    return
                }

                var parent = callExpression.parent
                while (parent !is PyExpressionStatement) {
                    parent = parent.parent
                }
                if (parent.getLastChild() !is PsiComment) {
                    registerSleepy(callExpression)
                }
            }
        }
    }
}