const url = "https://api.groq.com/openai/v1/chat/completions";
const key = "YOUR_API_KEY_HERE";

const prompt = `You are an AI task categorizer and prioritizer. Review the following pending tasks and assign a priority level (High, Medium, or Low) and a category (Work, Personal, Health, Finance, Errand) to each task. 
Respond strictly with a JSON object where the keys are the task IDs and the values are objects with "priority" and "category" string properties.
Example: {"taskId1": {"priority": "High", "category": "Work"}}
Tasks:
{ "id": "bcf123e4", "task": "i want to meet doctor", "category": "Work" }
{ "id": "aa123b34", "task": "hospital to doctor meet", "category": "Work" }
{ "id": "99bb123c", "task": "I have study", "category": "Work" }`;

async function test() {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${key}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      model: "llama-3.1-8b-instant",
      messages: [{ role: "user", content: prompt }],
      response_format: { type: "json_object" }
    })
  });
  const data = await response.json();
  console.log(JSON.stringify(data, null, 2));
}
test();
