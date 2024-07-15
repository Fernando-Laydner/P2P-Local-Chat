import java.util.*;
import java.io.*;


public class Client implements IStableMulticast, Serializable {

    public void deliver(String msg){
        System.out.println(msg);
    }

    public static void main(String[] args) {

        Client cliente = new Client();

        StableMulticast middleware = new StableMulticast("localhost", Integer.valueOf(args[0]), cliente);

        Scanner scanner = new Scanner(System.in);
        while (true){
            String command = scanner.nextLine();
            if (command.equals("#clientes")){
                middleware.getClients();
            }
            else if (command.equals("#buffer")){
                middleware.getBuffer_Timestamps();
            }
            else if (command.equals("#exit")){
                break;
            }
            else{
                middleware.msend(command);
            }
        }
        scanner.close();
    }
}