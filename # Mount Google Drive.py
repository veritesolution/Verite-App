# Mount Google Drive
from google.colab import drive
drive.mount('/content/drive')

# Create a directory in your Drive for the dataset
!mkdir -p "/content/drive/MyDrive/Datasets/BOAS_Sleep"

# Change to that directory
%cd "/content/drive/MyDrive/Datasets/BOAS_Sleep"

# Install AWS CLI
!pip install awscli

# Download the entire dataset directly to Google Drive
# WARNING: This is 33.45GB and will take a while
!aws s3 sync --no-sign-request \
  s3://openneuro.org/ds005555 \
  /content/drive/MyDrive/Datasets/BOAS_Sleep

# Verify the download
!ls -lh