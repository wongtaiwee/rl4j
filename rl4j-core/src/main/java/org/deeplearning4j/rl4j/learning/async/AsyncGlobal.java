package org.deeplearning4j.rl4j.learning.async;

import lombok.Getter;
import lombok.Setter;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.rl4j.network.NeuralNet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author rubenfiszel (ruben.fiszel@epfl.ch) on 8/5/16.
 *
 * In the original paper, the authors uses Asynchronous
 * Gradient Descent: Hogwild! It is a way to apply gradients
 * and modify a model in a lock-free manner.
 *
 * As a way to implement this with dl4j, it is unfortunately
 * necessary at the time of writing to apply the gradient
 * (update the parameters) on a single separate global thread.
 *
 * This Central thread for Asynchronous Method of reinforcement learning
 * enqueue the gradients coming from the different threads and update its
 * model and target. Those neurals nets are then synced by the other threads.
 *
 * The benefits of this thread is that the updater is "shared" between all thread
 * we have a single updater which is the single updater of the model contained here
 *
 * This is similar to RMSProp with shared g and momentum
 *
 * When Hogwild! is implemented, this could be replaced by a simple data
 * structure
 *
 *
 */
public class AsyncGlobal<NN extends NeuralNet> extends Thread {

    final private Logger log = LoggerFactory.getLogger("Global");
    final private NN current;
    final private ConcurrentLinkedQueue<Pair<Gradient[], Integer>> queue;
    final private AsyncConfiguration a3cc;
    @Getter
    private AtomicInteger T = new AtomicInteger(0);
    private NN target;
    @Getter @Setter
    private boolean running = true;


    public AsyncGlobal(NN initial, AsyncConfiguration a3cc) {
        this.current = initial;
        target = (NN) initial.clone();
        this.a3cc = a3cc;
        queue = new ConcurrentLinkedQueue<>();
    }



    public boolean isTrainingComplete() {
        return T.get() >= a3cc.getMaxStep();
    }


    synchronized public NN cloneCurrent() {
        return (NN) current.clone();
    }
    synchronized public NN cloneTarget() {
        return (NN) target.clone();
    }



    public void enqueue(Gradient[] gradient, Integer nstep) {
            queue.add(new Pair<>(gradient, nstep));
    }

    @Override
    public void run() {

                while (!isTrainingComplete() && running) {
                    if (!queue.isEmpty()) {
                        Pair<Gradient[], Integer> pair = queue.poll();
                        T.addAndGet(pair.getSecond());
                        Gradient[] gradient = pair.getFirst();
                        synchronized (this) {
                            current.applyGradient(gradient, pair.getSecond());
                        }
                        if (a3cc.getTargetDqnUpdateFreq() != -1 && T.get() / a3cc.getTargetDqnUpdateFreq() > (T.get() - pair.getSecond()) / a3cc.getTargetDqnUpdateFreq()) {
                            log.info("TARGET UPDATE at T = " + T.get());
                            synchronized (this) {
                                target = (NN) current.clone();
                            }
                        }
                    }
                }

            }

    }
