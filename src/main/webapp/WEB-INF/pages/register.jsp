<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE HTML>
<html>
<head>
    <meta charset="UTF-8">
    <title>Clash of Dynasties</title>
    <link rel="stylesheet" href="/css/theme.css" type="text/css">
    <script type="text/javascript" src="/lib/jquery-2.1.0.js"></script>
</head>
<body>
<div id="wrapper">
    <div id="body">
        <c:choose>
            <c:when test="${error}">
                <h1 style="color:#FF1A00;">Es existiert bereits ein Spieler mit diesem Namen</h1>
            </c:when>
            <c:otherwise>
                <h1>Registrieren</h1>
            </c:otherwise>
        </c:choose>
        <div id="content">
            <div style="width:317px; height:260px; margin: auto auto;">
                <form action="/step1" id="register" method="post">
                    <input type="hidden" name="key" value="${key}" />
                    <table>
                        <tr>
                            <td><label for="name">Name:</label></td>
                            <td><input type="text" id="name" name="name" /></td>
                        </tr>
                        <tr>
                            <td><label for="password">Passwort:</label></td>
                            <td><input type="password" id="password" name="password" /></td>
                        </tr>
                        <tr>
                            <td><label for="password">Passwort wiederholen:</label></td>
                            <td><input type="password" id="password2" name="password2" /></td>
                        </tr>
                        <tr>
                            <td><label for="email">EMail:</label></td>
                            <td><input type="text" id="email" name="email" /></td>
                        </tr>
                        <tr>
                            <td colspan="2"><button type="submit" style="width:100%">Weiter</button></td>
                        </tr>
                    </table>
                </form>
            </div>
        </div>
    </div>
</div>
<script>
    $("#register").submit(function(event) {
        if($("#name").val() == "" || $("#password").val() == "" || $("#email").val() == "") {
            alert("Bitte alle Felder ausfüllen!");
            event.preventDefault();
        }
        if($("#password").val() != $("#password2").val()) {
            alert("Passwörter stimmen nicht überein!");
            event.preventDefault();
        }

    });
</script>
</body>
</html>