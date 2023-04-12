/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sendfile.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.StringTokenizer;

/**
 *
 * @author hThao
 */
public class SocketThread implements Runnable {

    Socket socket;
    MainForm main;
    DataInputStream dis;
    StringTokenizer st;
    String client, filesharing_username;

    private final int BUFFER_SIZE = 100;

    public SocketThread(Socket socket, MainForm main) {
        this.main = main;
        this.socket = socket;

        try {
            dis = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            main.appendMessage("[SocketThreadIOException]: " + e.getMessage());
        }
    }

    private void createConnection(String receiver, String sender, String filename) {
        try {
            main.appendMessage("[createConnection]: đang tạo kết nối chia sẻ file.");
            Socket s = main.getClientList(receiver);
            if (s != null) { // Client đã tồn tại
                main.appendMessage("[createConnection]: Socket OK");
                DataOutputStream dosS = new DataOutputStream(s.getOutputStream());
                main.appendMessage("[createConnection]: DataOutputStream OK");
                // Format:  CMD_FILE_XD [sender] [receiver] [filename]
                String format = "CMD_FILE_XD " + sender + " " + receiver + " " + filename;
                dosS.writeUTF(format);
                main.appendMessage("[createConnection]: " + format);
            } else {// Client không tồn tại, gửi lại cho sender rằng receiver không tìm thấy.
                main.appendMessage("[createConnection]: Client không được tìm thấy '" + receiver + "'");
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.writeUTF("CMD_SENDFILEERROR " + "Client '" + receiver + "' không được tìm thấy trong danh sách, bảo đảm rằng user đang online.!");
            }
        } catch (IOException e) {
            main.appendMessage("[createConnection]: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                /**
                 * Phương thức nhận dữ liệu từ Client *
                 */
                String data = dis.readUTF();
                st = new StringTokenizer(data);
                String CMD = st.nextToken();
                /**
                 * Kiểm tra CMD *
                 */
                switch (CMD) {
                    case "CMD_JOIN":
                        /**
                         * CMD_JOIN [clientUsername] *
                         */
                        String clientUsername = st.nextToken();
                        client = clientUsername;// cạp nhật lại tên client khi có client mới 
                        main.setClientList(clientUsername);// thêm client vào danh sách client
                        main.setSocketList(socket); // thêm socket vào danh sách socket
                        main.appendMessage("[Client]: " + clientUsername + " tham gia chatroom.!"); // ghi text thông báo vào MainForm của server
                        break;

                    case "CMD_CHAT":
                        /**
                         * CMD_CHAT [from] [sendTo] [message] *
                         */
                        String from = st.nextToken();
                        String sendTo = st.nextToken();
                        String msg = "";
                        while (st.hasMoreTokens()) {
                            msg = msg + " " + st.nextToken();
                        }
                        Socket tsoc = main.getClientList(sendTo);
                        try {
                            DataOutputStream dos = new DataOutputStream(tsoc.getOutputStream());//ghi dl
                            /**
                             * CMD_MESSAGE *
                             */
                            String content = from + ": " + msg;
                            dos.writeUTF("CMD_MESSAGE " + content);
                            main.appendMessage("[Message]: Từ " + from + " Đến " + sendTo + " : " + msg);
                        } catch (IOException e) {
                            main.appendMessage("[IOException]: Không thể gửi tin nhắn đến " + sendTo);
                        }
                        break;
/*                      phương thức sử dụng một vòng lặp để duyệt qua danh sách các kết nối trên máy chủ (main.socketList), và gửi nội dung tin nhắn đến mỗi 
                        kết nối đó bằng cách sử dụng đối tượng DataOutputStream. Nếu việc gửi tin nhắn đến một kết nối nào đó thất bại, một thông báo lỗi sẽ 
                        được hiển thị.*/
                    case "CMD_CHATALL":
                        /**
                         * CMD_CHATALL [from] [message] *
                         */
                        String chatall_from = st.nextToken();// người gửi
                        String chatall_msg = ""; // nội dung tin nhắn
                        while (st.hasMoreTokens()) { 
                            chatall_msg = chatall_msg + " " + st.nextToken();
                        }                    
                        String chatall_content = chatall_from + " " + chatall_msg; // nội dung chat : Tên ng gửi + tin nhắn
                        //nội dung tin nhắn đó được gửi đến tất cả các kết nối trên máy chủ, ngoại trừ kết nối của người gửi tin nhắn.
                        for (int x = 0; x < main.clientList.size(); x++) { //duyệt qua danh sách các kết nối trên máy chủ (main.socketList)
                            if (!main.clientList.elementAt(x).equals(chatall_from)) { // gửi đến tất cả các kết nối trên máy chủ, ngoại trừ kết nối của người gửi tin nhắn.
                                try {
                                    Socket tsoc2 = (Socket) main.socketList.elementAt(x);// duyệt qua danh sách các kết nối trên máy chủ (main.socketList)
                                    DataOutputStream dos2 = new DataOutputStream(tsoc2.getOutputStream());// gửi nội dung tin nhắn đến mỗi kết nối đó bằng cách sử dụng đối tượng DataOutputStream.
                                    dos2.writeUTF("CMD_MESSAGE " + chatall_content);
                                } catch (IOException e) {
                                    main.appendMessage("[CMD_CHATALL]: " + e.getMessage());
                                }
                            }
                        }
                        main.appendMessage("[CMD_CHATALL]: " + chatall_content);
                        break;
/*
Khi nhận được gói tin với mã lệnh "CMD_SHARINGSOCKET", server sẽ tiến hành xử lý các thông tin kèm theo để thiết lập kết nối chia sẻ tập tin giữa các client. 
  server sẽ lấy tên người dùng của client gửi yêu cầu chia sẻ tập tin (được đưa vào gói tin trong biến "file_sharing_username") và lưu trữ thông tin này 
         trong biến "filesharing_username" của đối tượng "main" 

Sau đó, server sẽ thiết lập kết nối socket giữa client gửi yêu cầu và client sẽ nhận được yêu cầu chia sẻ tập tin (được lưu trữ trong biến "socket" của
      đối tượng SendFile ở phía client) bằng cách lưu trữ socket đó vào biến "ClientFileSharingSocket" của đối tượng "main". */
                    case "CMD_SHARINGSOCKET":
                        main.appendMessage("CMD_SHARINGSOCKET : Client thiết lập một socket cho kết nối chia sẻ file...");//hien thi thôg báo mainform server
                        String file_sharing_username = st.nextToken();
                        filesharing_username = file_sharing_username;//lúu ten ng dùng chia sẻ file
                        main.setClientFileSharingUsername(file_sharing_username);//thêm user vào client để chia sẽ file
                        main.setClientFileSharingSocket(socket);// thiết lập kết nối socket giữa client gửi yêu cầu và client sẽ nhận được yêu cầu chia sẻ tập tin được lưu trữ trong biến socket
                        //thong báo vào mainform server
                        main.appendMessage("CMD_SHARINGSOCKET : Username: " + file_sharing_username);
                        main.appendMessage("CMD_SHARINGSOCKET : Chia Sẻ File đang được mở");
                        break;

                    case "CMD_SENDFILE":
                        main.appendMessage("CMD_SENDFILE : Client đang gửi một file...");
                        /*
                         Format: CMD_SENDFILE [Filename] [Size] [Recipient] [Consignee]  from: Sender Format
                         Format: CMD_SENDFILE [Filename] [Size] [Consignee] to Receiver Format
                         */
                        String file_name = st.nextToken();
                        String filesize = st.nextToken();
                        String sendto = st.nextToken();
                        String consignee = st.nextToken();
                        main.appendMessage("CMD_SENDFILE : Từ: " + consignee);
                        main.appendMessage("CMD_SENDFILE : Đến: " + sendto);
                        /**
                         * Nhận client Socket *
                         */
                        main.appendMessage("CMD_SENDFILE : sẵn sàng cho các kết nối..");
                        Socket cSock = main.getClientFileSharingSocket(sendto); /* Consignee Socket  */
                        /*   Now Check if the consignee socket was exists.   */

                        if (cSock != null) { /* Exists   */

                            try {
                                main.appendMessage("CMD_SENDFILE : Đã được kết nối..!");
                                /**
                                 * Đầu tiên là viết filename..  *
                                 */
                                main.appendMessage("CMD_SENDFILE : đang gửi file đến client...");
                                DataOutputStream cDos = new DataOutputStream(cSock.getOutputStream());
                                cDos.writeUTF("CMD_SENDFILE " + file_name + " " + filesize + " " + consignee);
                                /**
                                 * Thứ hai là đọc nội dung file   *
                                 */
                                InputStream input = socket.getInputStream();
                                OutputStream sendFile = cSock.getOutputStream();
                                byte[] buffer = new byte[BUFFER_SIZE];
                                int cnt;
                                while ((cnt = input.read(buffer)) > 0) {
                                    sendFile.write(buffer, 0, cnt);
                                }
                                sendFile.flush();
                                sendFile.close();
                                /**
                                 * Xóa danh sách client *
                                 */
                                main.removeClientFileSharing(sendto);
                                main.removeClientFileSharing(consignee);
                                main.appendMessage("CMD_SENDFILE : File đã được gửi đến client...");
                            } catch (IOException e) {
                                main.appendMessage("[CMD_SENDFILE]: " + e.getMessage());
                            }
                        } else { /*   Không tồn tại, return error  */
                            /*   FORMAT: CMD_SENDFILEERROR  */

                            main.removeClientFileSharing(consignee);
                            main.appendMessage("CMD_SENDFILE : Client '" + sendto + "' không tìm thấy.!");
                            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                            dos.writeUTF("CMD_SENDFILEERROR " + "Client '" + sendto + "' không tìm thấy, Chia Sẻ File sẽ thoát.");
                        }
                        break;

                    case "CMD_SENDFILERESPONSE":
                        /*
                         Format: CMD_SENDFILERESPONSE [username] [Message]
                         */
                        String receiver = st.nextToken(); // phương thức nhận receiver username
                        String rMsg = ""; // phương thức nhận error message
                        main.appendMessage("[CMD_SENDFILERESPONSE]: username: " + receiver);
                        while (st.hasMoreTokens()) {
                            rMsg = rMsg + " " + st.nextToken();
                        }
                        try {
                            Socket rSock = (Socket) main.getClientFileSharingSocket(receiver);
                            DataOutputStream rDos = new DataOutputStream(rSock.getOutputStream());
                            rDos.writeUTF("CMD_SENDFILERESPONSE" + " " + receiver + " " + rMsg);
                        } catch (IOException e) {
                            main.appendMessage("[CMD_SENDFILERESPONSE]: " + e.getMessage());
                        }
                        break;
/*Trong đoạn mã trên, khi client nhận được chuỗi tin nhắn có định dạng là "CMD_SEND_FILE_XD", nó sẽ thực hiện các bước sau:

Sử dụng phương thức nextToken() để lấy ra các thông tin gửi kèm theo trong chuỗi tin nhắn, bao gồm:
send_sender: tên người gửi file
send_receiver: tên người nhận file
send_filename: tên file được gửi đi
Ghi log lại thông tin người gửi và tên file được gửi đi.

Gọi phương thức createConnection() để tạo kết nối và gửi file từ người gửi đến người nhận.*/
                    case "CMD_SEND_FILE_XD":  // Format: CMD_SEND_FILE_XD [sender] [receiver]                        
                        try {
                            String send_sender = st.nextToken();
                            String send_receiver = st.nextToken();
                            String send_filename = st.nextToken();
                            main.appendMessage("[CMD_SEND_FILE_XD]: Host: " + send_sender);
                            this.createConnection(send_receiver, send_sender, send_filename);
                        } catch (Exception e) {
                            main.appendMessage("[CMD_SEND_FILE_XD]: " + e.getLocalizedMessage());
                        }
                        break;

                    case "CMD_SEND_FILE_ERROR":  // Format:  CMD_SEND_FILE_ERROR [receiver] [Message]
                        String eReceiver = st.nextToken();
                        String eMsg = "";
                        while (st.hasMoreTokens()) {
                            eMsg = eMsg + " " + st.nextToken();
                        }
                        try {
                            /*  Gửi Error đến File Sharing host  */
                            Socket eSock = main.getClientFileSharingSocket(eReceiver); // phương thức nhận file sharing host socket cho kết nối
                            DataOutputStream eDos = new DataOutputStream(eSock.getOutputStream());
                            //  Format:  CMD_RECEIVE_FILE_ERROR [Message]
                            eDos.writeUTF("CMD_RECEIVE_FILE_ERROR " + eMsg);
                        } catch (IOException e) {
                            main.appendMessage("[CMD_RECEIVE_FILE_ERROR]: " + e.getMessage());
                        }
                        break;

                    case "CMD_SEND_FILE_ACCEPT": // Format:  CMD_SEND_FILE_ACCEPT [receiver] [Message]
                        String aReceiver = st.nextToken();
                        String aMsg = "";
                        while (st.hasMoreTokens()) {
                            aMsg = aMsg + " " + st.nextToken();
                        }
                        try {
                            /*  Send Error to the File Sharing host  */
                            Socket aSock = main.getClientFileSharingSocket(aReceiver); // get the file sharing host socket for connection
                            DataOutputStream aDos = new DataOutputStream(aSock.getOutputStream());
                            //  Format:  CMD_RECEIVE_FILE_ACCEPT [Message]
                            aDos.writeUTF("CMD_RECEIVE_FILE_ACCEPT " + aMsg);
                        } catch (IOException e) {
                            main.appendMessage("[CMD_RECEIVE_FILE_ERROR]: " + e.getMessage());
                        }
                        break;

                    default:
                        main.appendMessage("[CMDException]: Không rõ lệnh " + CMD);
                        break;
                }
            }
        } catch (IOException e) {
            /*   đây là hàm chatting client, remove nếu như nó tồn tại..   */
            System.out.println(client);
            System.out.println("File Sharing: " + filesharing_username);
            main.removeFromTheList(client);
            if (filesharing_username != null) {
                main.removeClientFileSharing(filesharing_username);
            }
            main.appendMessage("[SocketThread]: Kết nối client bị đóng..!");
        }
    }

}
