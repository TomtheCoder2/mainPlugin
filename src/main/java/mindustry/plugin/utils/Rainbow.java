package mindustry.plugin.utils;

import mindustry.plugin.ioMain;

public class Rainbow extends Thread {
    private Thread mt;

    public Rainbow(Thread mt) {
        this.mt = mt;
    }

    public void run() {
        while (this.mt.isAlive()) {
            try {
                Thread.sleep(100);
                ioMain.loop();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

