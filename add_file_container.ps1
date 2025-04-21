$container = "plank-db"
$localPath = "C:\srdev\task\plank-db\python\grover_ibm\grover.py"
$remotePath = "/app/python/grover.py"

Write-Host "📤 Copying updated script to container..."
docker cp $localPath "${container}:${remotePath}"

Write-Host "`n🔍 Verifying inside container (first 10 lines):"
docker exec -it $container sh -c "head -n 10 $remotePath"
