package org.nd4j.autodiff.gradcheck;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.util.DataTypeUtil;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@Slf4j
public class GradCheckReductions {

    static {
        Nd4j.create(1);
        DataTypeUtil.setDTypeForContext(DataBuffer.Type.DOUBLE);
    }

    @Test
    public void testReductionGradientsSimple() {
        //Test reductions: final and only function
        Nd4j.getRandom().setSeed(12345);

        for (int i = 0; i < 10; i++) {

            SameDiff sd = SameDiff.create();

            int nOut = 4;
            int minibatch = 10;
            SDVariable input = sd.var("in", new int[]{-1, nOut});

            SDVariable loss;
            String name;
            switch (i) {
                case 0:
                    loss = sd.mean("loss", input);
                    name = "mean";
                    break;
                case 1:
                    loss = sd.sum("loss", input);
                    name = "sum";
                    break;
                case 2:
                    loss = sd.standardDeviation("loss", input, true);
                    name = "stdev";
                    break;
                case 3:
                    loss = sd.min("loss", input);
                    name = "min";
                    break;
                case 4:
                    loss = sd.max("loss", input);
                    name = "max";
                    break;
                case 5:
                    loss = sd.variance("loss", input, true);
                    name = "variance";
                    break;
                case 6:
                    loss = sd.prod("loss", input);
                    name = "prod";
                    break;
                case 7:
                    loss = sd.norm1("loss", input);
                    name = "norm1";
                    break;
                case 8:
                    loss = sd.norm2("loss", input);
                    name = "norm2";
                    break;
                case 9:
                    loss = sd.normmax("loss", input);
                    name = "normmax";
                    break;
                default:
                    throw new RuntimeException();
            }


            String msg = "test: " + i + " - " + name;
            log.info("*** Starting test: " + msg);

            INDArray inputArr = Nd4j.randn(minibatch, nOut).muli(100);
            sd.associateArrayWithVariable(inputArr, input);

            boolean ok = GradCheckUtil.checkGradients(sd);

            assertTrue(msg, ok);
        }
    }

    @Test
    public void testReductionGradients1() {
        //Test reductions: final, but *not* the only function
        Nd4j.getRandom().setSeed(12345);

        List<String> allFailed = new ArrayList<>();

        for (int dim : new int[]{0, Integer.MAX_VALUE}) {    //These two cases are equivalent here

            for (int i = 0; i < 10; i++) {

                SameDiff sd = SameDiff.create();

                int nOut = 4;
                int minibatch = 10;
                SDVariable input = sd.var("in", new int[]{-1, nOut});
                SDVariable label = sd.var("label", new int[]{-1, nOut});

                SDVariable diff = input.sub(label);
                SDVariable sqDiff = diff.mul(diff);
                SDVariable msePerEx = sd.mean("msePerEx", sqDiff, 1);

                SDVariable loss;
                String name;
                switch (i) {
                    case 0:
                        loss = sd.mean("loss", msePerEx, dim);
                        name = "mean";
                        break;
                    case 1:
                        loss = sd.sum("loss", msePerEx, dim);
                        name = "sum";
                        break;
                    case 2:
                        loss = sd.standardDeviation("loss", msePerEx, true, dim);
                        name = "stdev";
                        break;
                    case 3:
                        loss = sd.min("loss", msePerEx, dim);
                        name = "min";
                        break;
                    case 4:
                        loss = sd.max("loss", msePerEx, dim);
                        name = "max";
                        break;
                    case 5:
                        loss = sd.variance("loss", msePerEx, true, dim);
                        name = "variance";
                        break;
                    case 6:
                        loss = sd.prod("loss", msePerEx, dim);
                        name = "prod";
                        break;
                    case 7:
                        loss = sd.norm1("loss", msePerEx, dim);
                        name = "norm1";
                        break;
                    case 8:
                        loss = sd.norm2("loss", msePerEx, dim);
                        name = "norm2";
                        break;
                    case 9:
                        loss = sd.normmax("loss", msePerEx, dim);
                        name = "normmax";
                        break;
                    default:
                        throw new RuntimeException();
                }


                String msg = "(test " + i + " - " + name + ", dimension=" + dim + ")";
                log.info("*** Starting test: " + msg);

                INDArray inputArr = Nd4j.randn(minibatch, nOut).muli(100);
                INDArray labelArr = Nd4j.randn(minibatch, nOut).muli(100);

                sd.associateArrayWithVariable(inputArr, input);
                sd.associateArrayWithVariable(labelArr, label);

                try {
                    INDArray out = sd.execAndEndResult();
                    assertNotNull(out);
                    assertArrayEquals(new int[]{1, 1}, out.shape());

//                    System.out.println(sd.asFlatPrint());

                    boolean ok = GradCheckUtil.checkGradients(sd);
                    if(!ok){
                        allFailed.add(msg);
                    }
                } catch (Exception e){
                    e.printStackTrace();
                    allFailed.add(msg + " - EXCEPTION");
                }
            }
        }

        assertEquals("Failed: " + allFailed, 0, allFailed.size());
    }

    @Test
    public void testReductionGradients2() {
        //Test reductions: NON-final function
        Nd4j.getRandom().setSeed(12345);

        int d0 = 3;
        int d1 = 4;
        int d2 = 5;

        List<String> allFailed = new ArrayList<>();
        for (int reduceDim : new int[]{0, 1, 2}) {
            for (int i = 0; i < 10; i++) {

                int[] outShape;
                switch (reduceDim) {
                    case 0:
                        outShape = new int[]{d1, d2};
                        break;
                    case 1:
                        outShape = new int[]{d0, d2};
                        break;
                    case 2:
                        outShape = new int[]{d0, d1};
                        break;
                    default:
                        throw new RuntimeException();
                }

                SameDiff sd = SameDiff.create();
                sd.setLogExecution(false);


                SDVariable in = sd.var("in", new int[]{-1, d1, d2});
//                SDVariable in = sd.var("in", new int[]{d0, d1, d2});
                SDVariable label = sd.var("label", outShape);
                SDVariable second = in.mul(2);

                SDVariable reduced;
                String name;
                switch (i) {
                    case 0:
                        reduced = sd.mean("reduced", second, reduceDim);
                        name = "mean";
                        break;
                    case 1:
                        reduced = sd.sum("reduced", second, reduceDim);
                        name = "sum";
                        break;
                    case 2:
                        reduced = sd.standardDeviation("reduced", second, true, reduceDim);
                        name = "stdev";
                        break;
                    case 3:
                        reduced = sd.min("reduced", second, reduceDim);
                        name = "min";
                        break;
                    case 4:
                        reduced = sd.max("reduced", second, reduceDim);
                        name = "max";
                        break;
                    case 5:
                        reduced = sd.variance("reduced", second, true, reduceDim);
                        name = "variance";
                        break;
                    case 6:
                        reduced = sd.prod("reduced", second, reduceDim);
                        name = "prod";
                        break;
                    case 7:
                        reduced = sd.norm1("reduced", second, reduceDim);
                        name = "norm1";
                        break;
                    case 8:
                        reduced = sd.norm2("reduced", second, reduceDim);
                        name = "norm2";
                        break;
                    case 9:
                        reduced = sd.normmax("reduced", second, reduceDim);
                        name = "normmax";
                        break;
                    default:
                        throw new RuntimeException();
                }

                SDVariable add = reduced.add(1.0);

                SDVariable diff = label.sub(add);
                SDVariable sqDiff = diff.mul(diff);
                SDVariable mseLoss = sd.mean("loss", sqDiff);


                String msg = "(test " + i + " - " + name + ", dimension=" + reduceDim + ")";
                log.info("*** Starting test: " + msg);

                INDArray inputArr = Nd4j.randn(new int[]{d0, d1, d2}).muli(1000);
                INDArray labelArr = Nd4j.randn(outShape).muli(1000);
                sd.associateArrayWithVariable(inputArr, in);
                sd.associateArrayWithVariable(labelArr, label);

                try {
                    boolean ok = GradCheckUtil.checkGradients(sd, 1e-5, 1e-5, 1e-4, true, false);
                    if(!ok){
                        allFailed.add(msg);
                    }
                } catch (Exception e){
                    e.printStackTrace();
                    allFailed.add(msg + " - EXCEPTION");
                }
            }
        }

        assertEquals("Failed: " + allFailed, 0, allFailed.size());
    }


    @Test
    public void testReduce3(){
        /*
        cosineSimilarity
        euclideanDistance
        manhattanDistance
         */

        Nd4j.getRandom().setSeed(12345);

        int d0 = 3;
        int d1 = 4;
        int d2 = 5;

        List<String> allFailed = new ArrayList<>();
        for (int[] reduceDims : new int[][]{{0}, {1}, {2}, {Integer.MAX_VALUE}, {0,1}, {0,2}, {1,2}, {0,1,2}}) {
            for (int i = 1; i < 2; i++) {

                SameDiff sd = SameDiff.create();
                sd.setLogExecution(false);


                SDVariable in = sd.var("in", new int[]{-1, d1, d2});
                SDVariable in2 = sd.var("in2", new int[]{-1,d1,d2});

                SDVariable reduced;
                String name;
                switch (i) {
                    case 0:
                        reduced = sd.manhattanDistance(in, in2, reduceDims);
                        name = "manhattan";
                        break;
                    case 1:
                        reduced = sd.euclideanDistance(in, in2, reduceDims);
                        name = "euclidean";
                        break;
                    case 2:
                        reduced = sd.cosineSimilarity(in, in2, reduceDims);
                        name = "cosine";
                        break;
                    default:
                        throw new RuntimeException();
                }

                SDVariable sum = sd.sum(reduced, Integer.MAX_VALUE);


                String msg = "(test " + i + " - " + name + ", dimensions=" + Arrays.toString(reduceDims) + ")";
                log.info("*** Starting test: " + msg);

                INDArray inArr = Nd4j.randn(new int[]{d0, d1, d2}).muli(100);
                INDArray in2Arr = Nd4j.randn(inArr.shape()).muli(100);
                sd.associateArrayWithVariable(inArr, in);
                sd.associateArrayWithVariable(in2Arr, in2);

                try {
                    boolean ok = GradCheckUtil.checkGradients(sd, 1e-5, 1e-5, 1e-4, true, false);
                    if(!ok){
                        allFailed.add(msg);
                    }
                } catch (Exception e){
                    e.printStackTrace();
                    allFailed.add(msg + " - EXCEPTION");
                }
            }
        }

        assertEquals("Failed: " + allFailed, 0, allFailed.size());
    }



}
