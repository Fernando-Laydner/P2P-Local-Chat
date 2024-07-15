import java.util.*;
import java.net.*;
import java.io.*;


class Message implements Serializable{
    private Integer[] timestamp;
    private String message;
    private String command;
    private ClientInfo client;
    private ArrayList<ClientInfo> clientes;


    public Message(Integer[] timestamp, String message, ClientInfo client, String command) {
        this.timestamp = timestamp;
        this.message = message;
        this.client = client;
        this.command = command;
    }

    public ClientInfo cliente(){
        return client;
    }

    public String command(){
        return command;
    }

    public String message(){
        return message;
    }

    public Integer[] timestamp(){
        return timestamp;
    }

    public void setClientList(ArrayList<ClientInfo> clientes){
        this.clientes = clientes;
    }

    public ArrayList<ClientInfo> getClientList(){
        return this.clientes;
    }
}

class ClientInfo implements Serializable{
    private String name;
    private Integer id;
    private InetAddress ip;
    private Integer porta;
    private IStableMulticast client;
    
    public ClientInfo(InetAddress ip, Integer porta, String name, IStableMulticast client){
        this.ip = ip;
        this.porta = porta;
        this.client = client;
        this.name = name;
        this.id = -1;
    }

    public IStableMulticast getClient(){
        return client;
    }

    public InetAddress getIP(){
        return ip;
    }

    public Integer getPort(){
        return porta;
    }

    public String getName(){
        return name;
    }

    public Integer getID(){
        return id;
    }

    public void setID(Integer id){
        this.id = id;
    }
}


public class StableMulticast implements Serializable{
    // Tamanho do grupo
    final private Integer group_size = 3;

    // Multicast
    private String ip = "224.0.5.1";
    private Integer porta = 1236;
    private InetAddress multicast;
    private MulticastSocket socket;

    // Unicast
    private String ip_unicast;
    private Integer porta_unicast;
    private InetAddress unicast;
    private DatagramSocket socket_unicast;
    
    // Informacoes do cliente
    private ClientInfo client;

    // Buffer e timestamps
    private List<Message> buffer;
    private Integer[][] MCi;

    // Informacoes dos outros clientes
    private ArrayList<ClientInfo> clientes;
    
    // Outros
    public final String ANSI_RESET = "\u001B[0m";
    public final String ANSI_GREEN = "\u001B[32m";
    public final String ANSI_YELLOW = "\u001B[33m";
    private Scanner input;

    public StableMulticast(String ip, Integer port, IStableMulticast client) {
        // Recebe o endereço e porta unicast
        this.ip_unicast = ip;
        this.porta_unicast = port;

        // Cria as arrays necessárias
        this.clientes = new ArrayList<>();
        this.MCi = new Integer[group_size][group_size];
        for (Integer[] row : this.MCi) {
            Arrays.fill(row, -1);
        }
        this.buffer = new ArrayList<>();        

        // Coletando o nome do cliente
        this.input = new Scanner(System.in);
        System.out.println("Digite um nome:");
        String nome = input.nextLine();

        // Inicia os sockets
        try{
            this.multicast = InetAddress.getByName(this.ip);
            this.socket = new MulticastSocket(this.porta);

            this.unicast = InetAddress.getByName(this.ip_unicast);
            this.socket_unicast = new DatagramSocket(this.porta_unicast, this.unicast);

            NetworkInterface netIf = NetworkInterface.getByName("eth0");
            socket.joinGroup(new InetSocketAddress(this.multicast, this.porta), netIf);
            socket.setReuseAddress(true);           
        }
        catch (Exception e) {
            System.err.println("Erro ao criar o socket!");
            e.printStackTrace();
            return;
        }

        // Cria os listeners para cada socket
        listening(this.socket); 
        listening(this.socket_unicast);

        // Cria a mensagem de join e envia em multicast pra todos
        this.client = new ClientInfo(unicast, porta_unicast, nome, client);
        Message anyone = new Message(null, "", this.client, "join");
        message_send(anyone, this.socket, this.multicast, this.porta);
    }

    public void getBuffer_Timestamps(){
        List<String> temp = new ArrayList<>();
        for (Message msg : buffer){
            temp.add(msg.message());
        }
        System.out.println("Buffer: " + temp);
        System.out.print("Timestamps: ");
        for (int i = 0; i < group_size; i++) {
            if (i == 0){
                System.out.print("[");
            }
            else{
                System.out.print("            [");
            }
            for (int j = 0; j < group_size-1; j++) {
                System.out.print(MCi[i][j] + ", ");
            }
            System.out.print(MCi[i][group_size-1] + "]");
            System.out.println();
        }
    }

    public void getClients(){
        for (ClientInfo each : this.clientes){
            System.out.println(each.getName());
        }
    }

    private int getMin(Integer sender){
        Integer val = 0;
        int min = 9999;
        while (val < group_size){
            if (MCi[val][sender] < min){
                min = MCi[val][sender];
            }
            val++;
        }
        return min;
    }

    private void checkBuffer(){
        if (!buffer.isEmpty()){
            Integer val = 0;
            while (val < buffer.size()){
                Message msg = buffer.get(val);
                if (msg.timestamp()[msg.cliente().getID()] <= getMin(msg.cliente().getID())){
                    buffer.remove(msg);
                    //System.out.println("Removido do buffer: " + msg.message());
                    val--;
                }
                val++;
            }
        }
    }

    private byte[] serialize(Object obg){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try{
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(obg);
        } catch (Exception e) {
            System.err.println("Erro ao serializar o Objeto!");
            e.printStackTrace();
        }
        return baos.toByteArray();
    }

    private void message_send(Message obg, DatagramSocket skt, InetAddress address, Integer port){
        try{
            byte[] sendData = serialize(obg);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
            skt.send(sendPacket);
        }
        catch (Exception e) {
            System.err.println("Erro ao enviar o Objeto em unicast!");
            e.printStackTrace();
        }
    }

    private Object deserialize(byte[] data){
        try{
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (Exception e) {
            System.err.println("Erro ao deserializar o Objeto!");
            e.printStackTrace();
        }
        return null;
    }

    private Message message_receive(DatagramSocket skt){
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        try{
            skt.receive(receivePacket);            
            return (Message) deserialize(receivePacket.getData());
        }
        catch (Exception e) {
            System.err.println("Erro ao receber o Objeto!");
            e.printStackTrace();
        }
        return null;
    }

    private synchronized void listening(DatagramSocket skt){
        Thread udpThread = new Thread(() -> {
            try {
                // Loop para receber mensagens.
                while (!Thread.currentThread().isInterrupted()) {
                    Message sms = message_receive(skt);
                    switch (sms.command()) {
                        case "msg":
                            this.buffer.add(sms);
                            if (sms.cliente().getID().intValue() != this.client.getID().intValue()){
                                // Essa linha deveria estar dentro do if no projeto do trabalho...
                                this.MCi[sms.cliente().getID()] = sms.timestamp();

                                this.MCi[this.client.getID()][sms.cliente().getID()]++;
                            }
                            this.client.getClient().deliver(sms.message());
                            checkBuffer();
                            getBuffer_Timestamps();
                            break;
                        case "join":
                            sms.cliente().setID(this.clientes.size());
                            clientes.add(sms.cliente());
                            this.client.getClient().deliver(ANSI_GREEN + "Bem vindo(a) " + sms.cliente().getName() + " ao chat!" + ANSI_RESET);

                            Message hello = new Message(null, "", this.client, "hello");
                            hello.setClientList(clientes);
                            message_send(hello, this.socket_unicast, sms.cliente().getIP(), sms.cliente().getPort());                                
                            break;
                        case "hello":
                            this.clientes = sms.getClientList();
                            this.client.setID(this.clientes.size() - 1);
                            this.MCi[this.client.getID()][this.client.getID()] = 0;
                            break;
                    }
                }           
            } 
            catch (Exception e) {
                System.err.println("Erro ao receber mensagens!");
                e.printStackTrace();
            }
        });
        udpThread.start();
    }

    public synchronized void msend(String msg) {
        msg = this.ANSI_YELLOW +  this.client.getName() + ": " + this.ANSI_RESET + msg;
        Message msn = new Message(Arrays.stream(MCi[this.client.getID()]).toArray(Integer[]::new), msg, this.client, "msg");
        this.MCi[this.client.getID()][this.client.getID()]++;

        System.out.println("Enviar para todos? 's' ou 'n'.");
        String answer = this.input.nextLine();
        switch (answer) {
            case "s":
                for (ClientInfo each : this.clientes){
                    message_send(msn, this.socket_unicast, each.getIP(), each.getPort());
                }
                break;
            case "n":
                for (ClientInfo each : this.clientes){
                    if (each.getID().intValue() != this.client.getID()){
                        System.out.println("Digite enter para enviar para: " + ANSI_YELLOW + each.getName() + ANSI_RESET);
                        answer = this.input.nextLine();
                    }
                    message_send(msn, this.socket_unicast, each.getIP(), each.getPort());
                }
                break;
            default:
                System.err.println("Comando não reconhecido!!!");
                break;
        }
    }
}
