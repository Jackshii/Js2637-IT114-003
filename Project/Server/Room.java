package Project.Server;

import java.util.concurrent.ConcurrentHashMap;

import Project.Common.*;
import Project.Server.Room.TextFormatter;

import java.util.*;

public class Room implements AutoCloseable{
    private String name;// unique name of the Room
    protected volatile boolean isRunning = false;
    private ConcurrentHashMap<Long, ServerThread> clientsInRoom = new ConcurrentHashMap<Long, ServerThread>();

    public final static String LOBBY = "lobby";

    private void info(String message) {
        LoggerUtil.INSTANCE.info(String.format("Room[%s]: %s", name, message));
    }

    public Room(String name) {
        this.name = name;
        isRunning = true;
        info("created");
    }

    public String getName() {
        return this.name;
    }

    protected synchronized void addClient(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        if (clientsInRoom.containsKey(client.getClientId())) {
            info("Attempting to add a client that already exists in the room");
            return;
        }
        clientsInRoom.put(client.getClientId(), client);
        client.setCurrentRoom(this);

        // notify clients of someone joining
        sendRoomStatus(client.getClientId(), client.getClientName(), true);
        // sync room state to joiner
        syncRoomList(client);

        info(String.format("%s[%s] joined the Room[%s]", client.getClientName(), client.getClientId(), getName()));

    }

    protected synchronized void removedClient(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        // notify remaining clients of someone leaving
        // happen before removal so leaving client gets the data
        sendRoomStatus(client.getClientId(), client.getClientName(), false);
        clientsInRoom.remove(client.getClientId());
        LoggerUtil.INSTANCE.fine("Clients remaining in Room: " + clientsInRoom.size());

        info(String.format("%s[%s] left the room", client.getClientName(), client.getClientId(), getName()));

        autoCleanup();

    }

    /**
     * Takes a ServerThread and removes them from the Server
     * Adding the synchronized keyword ensures that only one thread can execute
     * these methods at a time,
     * preventing concurrent modification issues and ensuring thread safety
     * 
     * @param client
     */
    protected synchronized void disconnect(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        long id = client.getClientId();
        sendDisconnect(client);
        client.disconnect();
        // removedClient(client); // <-- use this just for normal room leaving
        clientsInRoom.remove(client.getClientId());
        LoggerUtil.INSTANCE.fine("Clients remaining in Room: " + clientsInRoom.size());
        
        // Improved logging with user data
        info(String.format("%s[%s] disconnected", client.getClientName(), id));
        autoCleanup();
    }

    protected synchronized void disconnectAll() {
        info("Disconnect All triggered");
        if (!isRunning) {
            return;
        }
        clientsInRoom.values().removeIf(client -> {
            disconnect(client);
            return true;
        });
        info("Disconnect All finished");
        autoCleanup();
    }

    /**
     * Attempts to close the room to free up resources if it's empty
     */
    private void autoCleanup() {
        if (!Room.LOBBY.equalsIgnoreCase(name) && clientsInRoom.isEmpty()) {
            close();
        }
    }

    public void close() {
        // attempt to gracefully close and migrate clients
        if (!clientsInRoom.isEmpty()) {
            sendMessage(null, "Room is shutting down, migrating to lobby");
            info(String.format("migrating %s clients", name, clientsInRoom.size()));
            clientsInRoom.values().removeIf(client -> {
                Server.INSTANCE.joinRoom(Room.LOBBY, client);
                return true;
            });
        }
        Server.INSTANCE.removeRoom(this);
        isRunning = false;
        clientsInRoom.clear();
        info(String.format("closed", name));
    }

    // send/sync data to client(s)

    /**
     * Sends to all clients details of a disconnect client
     * @param client
     */
    protected synchronized void sendDisconnect(ServerThread client) {
        info(String.format("sending disconnect status to %s recipients", clientsInRoom.size()));
        clientsInRoom.values().removeIf(clientInRoom -> {
            boolean failedToSend = !clientInRoom.sendDisconnect(client.getClientId(), client.getClientName());
            if (failedToSend) {
                info(String.format("Removing disconnected client[%s] from list", client.getClientId()));
                disconnect(client);
            }
            return failedToSend;
        });
    }

    /**
     * Syncs info of existing users in room with the client
     * 
     * @param client
     */
    protected synchronized void syncRoomList(ServerThread client) {

        clientsInRoom.values().forEach(clientInRoom -> {
            if (clientInRoom.getClientId() != client.getClientId()) {
                client.sendClientSync(clientInRoom.getClientId(), clientInRoom.getClientName());
            }
        });
    }

    /**
     * Syncs room status of one client to all connected clients
     * 
     * @param clientId
     * @param clientName
     * @param isConnect
     */
    protected synchronized void sendRoomStatus(long clientId, String clientName, boolean isConnect) {
        info(String.format("sending room status to %s recipients", clientsInRoom.size()));
        clientsInRoom.values().removeIf(client -> {
            boolean failedToSend = !client.sendRoomAction(clientId, clientName, getName(), isConnect);
            if (failedToSend) {
                info(String.format("Removing disconnected client[%s] from list", client.getClientId()));
                disconnect(client);
            }
            return failedToSend;
        });
    }

    /**
     * Sends a basic String message from the sender to all connectedClients
     * Internally calls processCommand and evaluates as necessary.
     * Note: Clients that fail to receive a message get removed from
     * connectedClients.
     * Adding the synchronized keyword ensures that only one thread can execute
     * these methods at a time,
     * preventing concurrent modification issues and ensuring thread safety
     * 
     * @param message
     * @param sender  ServerThread (client) sending the message or null if it's a
     *                server-generated message
     */
    protected synchronized void sendMessage(ServerThread sender, String message) {
    if (!isRunning) { // block action if Room isn't running
        return;
    }

    // Format the message using the TextFormatter class
    final String formattedMessage = TextFormatter.formatText(message);

    // Proceed to send the formatted message to all clients
    long senderId = sender == null ? ServerThread.DEFAULT_CLIENT_ID : sender.getClientId();
    info(String.format("sending message to %s recipients: %s", clientsInRoom.size(), formattedMessage));
    clientsInRoom.values().removeIf(client -> {
        boolean failedToSend = !client.sendMessage(senderId, formattedMessage);
        if (failedToSend) {
            info(String.format("Removing disconnected client[%s] from list", client.getClientId()));
            disconnect(client);
        }
        return failedToSend;
    });
}
    // end send data to client(s)

    // receive data from ServerThread
    
    protected void handleCreateRoom(ServerThread sender, String room) {
        if (Server.INSTANCE.createRoom(room)) {
            Server.INSTANCE.joinRoom(room, sender);
        } else {
            sender.sendMessage(String.format("Room %s already exists", room));
        }
    }

    protected void handleJoinRoom(ServerThread sender, String room) {
        if (!Server.INSTANCE.joinRoom(room, sender)) {
            sender.sendMessage(String.format("Room %s doesn't exist", room));
        }
    }

    protected void handleListRooms(ServerThread sender, String roomQuery){
        sender.sendRooms(Server.INSTANCE.listRooms(roomQuery));
    }

    protected void clientDisconnect(ServerThread sender) {
        disconnect(sender);
    }
    
    protected void handleRoll(ServerThread sender, int numdice, int diceside) {
        String clientName = sender.getClientName();
        
        Random random = new Random();
        int total = 0;
        String rollResults = "";
        
        for (int i = 0; i < numdice; i++) {
            int roll = random.nextInt(diceside) + 1;  
            total += roll;  
            rollResults += roll + " "; 
        }
        rollResults = rollResults.trim();
        String resultMessage ="";
        if (numdice==1)
        {
            resultMessage = clientName + " rolled " + diceside + " sided dice and got " + total ;
           
        }
        else
        {
            resultMessage = clientName + " rolled " + numdice + "d" + diceside + " and got " + total + " (Rolls: " + rollResults + ")";
        }
        
        sendMessage(null, resultMessage);  
    }


    protected void handleFlip(ServerThread sender) {
        String clientName = sender.getClientName();
        Random random = new Random();
        int isHeads = random.nextInt(2); 
    
        String resultMessage;
        if (isHeads == 0) {
            resultMessage = String.format("%s flipped a coin and got Tails", clientName);
        } else {
            resultMessage = String.format("%s flipped a coin and got Heads", clientName);
        }
        sendMessage(null, resultMessage);  // Send the result message to all clients in the room
    }
    

    //js2637 11/10/2024
    //worked on it with my brother es525 from it114 
    public class TextFormatter {
    
        public static String formatText(String message) {

            message = message.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
            message = message.replaceAll("\\*(.*?)\\*", "<i>$1</i>");
            message = message.replaceAll("_(.*?)_", "<u>$1</u>");
            message = message.replaceAll("#r(.*?)r#", "<red>$1</red>");
            message = message.replaceAll("#g(.*?)g#", "<green>$1</green>");
            message = message.replaceAll("#b(.*?)b#", "<blue>$1</blue>");
            
            return message;
        }
    }

 

    // end receive data from ServerThread
}
