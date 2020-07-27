<?php
    include('./library/connect.php');
    $conn = connect();

    
    $post = file_get_contents("php://input");
    $json = json_decode($post, true);

    $result = array();
    if ($json == null){
        $result["result"] = "fail";
        $result["comment"] = "json error";
        echo(json_encode($result));
        disconnect($conn);
        return;
    }

    $id = $json['id'];
    $session = $json['session'];

    removeSession($conn, $id, $session);

    disconnect($conn);
?>