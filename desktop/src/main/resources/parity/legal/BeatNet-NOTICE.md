# BeatNet model notice

MeloX includes a Core ML conversion of the generic BeatNet model by Mojtaba
Heydari.

- Source: https://github.com/mjhydri/BeatNet
- Paper: “BeatNet: CRNN and Particle Filtering for Online Joint Beat,
  Downbeat, Tempo and Meter Tracking”
- Original model license: Creative Commons Attribution 4.0 International
  (CC BY 4.0)
- Changes: the PyTorch network was exported as a fixed 32-second Core ML
  ML Program with FP16 compute precision. The model returns beat and downbeat
  activation probabilities; feature extraction and temporal decoding run in
  MeloX.

The full license text is available at:
https://creativecommons.org/licenses/by/4.0/legalcode
