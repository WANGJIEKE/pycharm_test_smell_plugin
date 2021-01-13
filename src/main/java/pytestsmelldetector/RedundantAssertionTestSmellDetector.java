package pytestsmelldetector;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedundantAssertionTestSmellDetector extends AbstractTestSmellDetector {
    private final HashMap<PyFunction, Integer> testMethodHaveRedundantAssertion;

    private static final Logger LOG = Logger.getInstance(RedundantAssertionTestSmellDetector.class);

    class RedundantAssertionVisitor extends MyPsiElementVisitor {
        public void visitPyCallExpression(PyCallExpression callExpression) {
            PsiElement child = callExpression.getFirstChild();
            if (!(child instanceof PyReferenceExpression) || !Util.isCallAssertMethod((PyReferenceExpression) child)) {
                return;
            }

            List<PyExpression> argList = callExpression.getArguments(null);
            if (Util.ASSERT_METHOD_ONE_PARAM.containsKey(((PyReferenceExpression) child).getName())) {
                if (argList.get(0).getText().equals(Util.ASSERT_METHOD_ONE_PARAM.get(((PyReferenceExpression) child).getName()))) {
                    testMethodHaveRedundantAssertion.replace(
                            currentMethod,
                            testMethodHaveRedundantAssertion.get(currentMethod) + 1
                    );
                }
            } else if (Util.ASSERT_METHOD_TWO_PARAMS.contains(((PyReferenceExpression) child).getName())) {
                if (argList.get(0).getText().equals(argList.get(1).getText())) {
                    testMethodHaveRedundantAssertion.replace(
                            currentMethod,
                            testMethodHaveRedundantAssertion.get(currentMethod) + 1
                    );
                }
            }
        }
    }

    private final RedundantAssertionVisitor visitor = new RedundantAssertionVisitor();

    public RedundantAssertionTestSmellDetector(PyClass aTestCase) {
        testCase = aTestCase;
        testMethodHaveRedundantAssertion = new HashMap<>();
    }

    @Override
    public void analyze() {
        List<PyFunction> testMethods = Util.gatherTestMethods(testCase);
        for (PyFunction testMethod : testMethods) {
            currentMethod = testMethod;
            testMethodHaveRedundantAssertion.put(currentMethod, 0);
            visitor.visitElement(currentMethod);
        }
        currentMethod = null;
    }

    @Override
    public void reset() {
        currentMethod = null;
        testMethodHaveRedundantAssertion.clear();
    }

    @Override
    public void reset(PyClass aTestCase) {
        testCase = aTestCase;
        currentMethod = null;
        testMethodHaveRedundantAssertion.clear();
    }

    @Override
    public String getSmellName() {
        return "Redundant Assertion";
    }

    @Override
    public String getSmellDetail() {
        return testMethodHaveRedundantAssertion.toString();
    }

    public HashMap<PyFunction, Integer> getTestMethodHaveRedundantAssertion() {
        return testMethodHaveRedundantAssertion;
    }
}
