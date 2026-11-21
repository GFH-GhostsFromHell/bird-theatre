"""Pretrained bird localization (Torchvision) + species ID (BioCLIP 2).

Images are processed locally; model assets are downloaded on first use.
Species scores compare ONLY candidates in a user-provided CSV, not all bird taxa.
"""
import csv
from pathlib import Path


def load_species(path: str) -> list[tuple[str, str]]:
    with open(path, newline="", encoding="utf-8-sig") as file:
        rows = list(csv.DictReader(file))
    names = []
    for row in rows:
        common = row["common_name"].strip()
        scientific = row["scientific_name"].strip()
        if common and scientific:
            names.append((common, scientific))
    if not 2 <= len(names) <= 2000 or len({x[0] for x in names}) != len(names):
        raise ValueError("Species CSV needs 2-2000 distinct common names")
    return names


class TorchvisionBirdDetector:
    def __init__(self, device="cpu", threshold=0.65):
        import torch
        from torchvision.models.detection import (
            fasterrcnn_mobilenet_v3_large_320_fpn,
            FasterRCNN_MobileNet_V3_Large_320_FPN_Weights,
        )
        from torchvision.transforms.functional import to_tensor
        weights = FasterRCNN_MobileNet_V3_Large_320_FPN_Weights.DEFAULT
        self.bird_class = weights.meta["categories"].index("bird")
        self.model = fasterrcnn_mobilenet_v3_large_320_fpn(weights=weights).to(device).eval()
        self.device = device
        self.torch = torch
        self.to_tensor = to_tensor
        self.threshold = threshold

    def detect(self, image):
        import PIL.Image
        if not isinstance(image, PIL.Image.Image):
            raise TypeError("Pass a PIL RGB image")
        with self.torch.inference_mode():
            out = self.model([self.to_tensor(image).to(self.device)])[0]
        boxes = []
        for box, label, score in zip(out["boxes"], out["labels"], out["scores"]):
            if int(label) == self.bird_class and float(score) >= self.threshold:
                boxes.append((tuple(float(x) for x in box.cpu().tolist()), float(score)))
        return boxes


class BioClipSpeciesClassifier:
    def __init__(self, species: list[tuple[str, str]], device="cpu", min_similarity=0.18, min_margin=0.025):
        import torch
        import open_clip
        self.device, self.torch = device, torch
        self.names = [common for common, _ in species]
        self.min_similarity, self.min_margin = min_similarity, min_margin
        model_id = "hf-hub:imageomics/bioclip-2"
        self.model, _, self.preprocess = open_clip.create_model_and_transforms(model_id)
        self.model = self.model.to(device).eval()
        tokenizer = open_clip.get_tokenizer(model_id)
        prompts = [f"a clear photo of a {common} ({scientific}), a wild bird" for common, scientific in species]
        with torch.inference_mode():
            encoded = self.model.encode_text(tokenizer(prompts).to(device), normalize=True)
            self.text_features = encoded / encoded.norm(dim=-1, keepdim=True)

    def classify(self, image):
        with self.torch.inference_mode():
            data = self.preprocess(image).unsqueeze(0).to(self.device)
            image_feature = self.model.encode_image(data, normalize=True)
            similarities = (image_feature @ self.text_features.T).flatten()
            probabilities = (20 * similarities).softmax(dim=0)
            ranked = similarities.topk(2)
            top_index = int(ranked.indices[0])
            top_similarity = float(ranked.values[0])
            margin = top_similarity - float(ranked.values[1])
            # Unknown on low similarity or a near tie. These scores are NOT calibrated probabilities.
            if top_similarity < self.min_similarity or margin < self.min_margin:
                return "Unidentified", 0.0
            return self.names[top_index], float(probabilities[top_index])


def crop_bird(image, box, pad_fraction=0.12):
    width, height = image.size
    x1, y1, x2, y2 = box
    pad = max(2, (x2 - x1) * pad_fraction, (y2 - y1) * pad_fraction)
    coords = (max(0, int(x1 - pad)), max(0, int(y1 - pad)),
              min(width, int(x2 + pad)), min(height, int(y2 + pad)))
    if coords[0] >= coords[2] or coords[1] >= coords[3]:
        raise ValueError("Invalid detection box")
    return image.crop(coords)
