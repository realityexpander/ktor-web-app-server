package testUtils

import kotlinx.coroutines.Job

fun Job.waitForJobToComplete() {
        this.start()

        while (!this.isCompleted) {
            println("waiting...")
            Thread.sleep(20)
        }
    }