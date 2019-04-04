package com.mango.cluster;

import com.mango.cluster.main.Master;
import com.mango.cluster.main.Worker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClusterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClusterApplication.class, args);
        if(args[1].equals("Master")){
            Master.main(args);
        }else if(args[1].equals("Worker")){
            Worker.main(args);
        }else {
            System.out.println("请输入正确的启动类");
        }
    }

}
