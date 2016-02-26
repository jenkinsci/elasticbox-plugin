/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

import java.util.ArrayDeque;
import java.util.Queue;

public class Test {

    public  static int counter = 0;

    public static void main(String[] args) {

        String[] elements = "1,2,3,4".split(",");
        int m = elements.length;   //Elementos elegidos

        int n = 3;                  //Tipos para escoger

//        varR(elements, "", n);

        final int count = varI(elements, n);

        System.out.println("TOT:" + count);
    }

    private static void varR(String[] elements, String group, int groupLength) {
        if (groupLength == 0) {
            System.out.println(group);
            counter++;
        } else {
            for (int i = 0; i < elements.length; i++) {
                varR(elements, group + elements[i], groupLength - 1);
            }
        }
    }

    private static int varI(String[] elements, int groupLength) {


        Queue<String> queue = new ArrayDeque<>();
        for (int i = 0; i < elements.length; i++) {
            queue.add(elements[i]);
        }

        while (queue.peek().length() < groupLength) {

            final String poll = queue.poll();

            for (int i = 0; i < elements.length; i++) {
                queue.add(poll + elements[i]);
            }

        }

        for (String s : queue) {
            System.out.println(s);
        }

        return queue.size();

    }

}