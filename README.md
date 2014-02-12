lftp_mirroring
==============

lftp_mirroring


Script allows to mirror folder content between networks if no direct access.

In this case remote network was accessible only via VPN. From inside remote network outer internets are accessible via proxy.

This scripts have two parts

Remote part is running on some host inside VPN, scans particular folder and uploads changes to intermediate ftp via lftp through proxy.


Local side scans ftp, dowloads and unpacks newly added files via winscp



remote profile for lftp should be set

script for local winscp and connection bookmark should be set 