<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <title>Welcome to Smart Hiring Assistant</title>
  <style>
    body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin:0; padding:0; }
    .container { max-width:600px; margin:40px auto; background:#fff; border-radius:8px;
                 padding:32px; box-shadow:0 2px 8px rgba(0,0,0,0.1); }
    h1 { color:#2c3e50; }
    p  { color:#555; line-height:1.6; }
    .badge { display:inline-block; background:#3498db; color:#fff; border-radius:4px;
             padding:4px 12px; font-size:13px; }
    .footer { margin-top:32px; font-size:12px; color:#aaa; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Welcome, ${firstName}!</h1>
    <p>Your account on <strong>Smart Hiring Assistant</strong> has been created successfully.</p>
    <p>Username: <strong>${username}</strong><br/>
       Role: <span class="badge">${role}</span></p>
    <p>You can now log in and explore the platform.</p>
    <div class="footer">
      &copy; Smart Hiring Assistant &mdash; automated notification
    </div>
  </div>
</body>
</html>
