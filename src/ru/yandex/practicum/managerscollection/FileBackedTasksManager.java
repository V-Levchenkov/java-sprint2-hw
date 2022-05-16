package ru.yandex.practicum.managerscollection;

import ru.yandex.practicum.exception.ManagerSaveException;
import ru.yandex.practicum.managerscollection.interfaces.HistoryManager;
import ru.yandex.practicum.managerscollection.interfaces.TaskManager;
import ru.yandex.practicum.managerscollection.interfaces.TaskStatus;
import ru.yandex.practicum.managerscollection.interfaces.TypeTask;
import ru.yandex.practicum.tasks.Epic;
import ru.yandex.practicum.tasks.Subtask;
import ru.yandex.practicum.tasks.Task;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


import static ru.yandex.praktikum.utils.CSVutil.splitter;


class Main {
    public static void main(String[] args) throws ManagerSaveException {

        FileBackedTasksManager fileBackedTasksManager = new FileBackedTasksManager(new File("savedData.csv"));


        Task task1 = new Task("Задача №1", "Описание задачи №1 ", TaskStatus.NEW);
        fileBackedTasksManager.addTask(task1);

        Task task2 = new Task("Задача №2", "Описание задачи №2 ", TaskStatus.NEW);
        fileBackedTasksManager.addTask(task2);

        // Создаем 2 эпика, первый в 3-мя подзадачами
        Epic epic1 = new Epic("Убраться в квартире", "Пропылесосить", TaskStatus.NEW);
        fileBackedTasksManager.addEpic(epic1);

        Subtask subtask1 = new Subtask("Сделать уборку на балконе", "Протереть окна", TaskStatus.NEW, (epic1.getTaskId()));
        fileBackedTasksManager.addSubTask(subtask1);

        Subtask subtask2 = new Subtask("Сделать уборку в гардеробной", "Разобрать вещи", TaskStatus.NEW, (epic1.getTaskId()));
        fileBackedTasksManager.addSubTask(subtask2);

        Subtask subtask3 = new Subtask("Сделать уборку на убраться в спальне", "Помыть окна", TaskStatus.NEW, (epic1.getTaskId()));
        fileBackedTasksManager.addSubTask(subtask3);



        // Второй без подзадач
        Epic epic2 = new Epic("Помыть машину", "Нужна химчистка багажника", TaskStatus.NEW);
        fileBackedTasksManager.addEpic(epic2);
        System.out.println("\n");
        // Последовательная история вызова
        fileBackedTasksManager.getTaskById(1L);
        fileBackedTasksManager.getTaskById(2L);
        fileBackedTasksManager.getEpicById(3L);
        fileBackedTasksManager.getSubTaskById(6L);
        fileBackedTasksManager.getSubTaskById(5L);
        fileBackedTasksManager.getSubTaskById(4L);
        fileBackedTasksManager.getEpicById(7L);
        System.out.println("Последовательная история вызова: " + fileBackedTasksManager.history());

    }
}

public class FileBackedTasksManager extends InMemoryTaskManager implements TaskManager {
    private final File fileName;// путь к файлу сохранения задач

    public FileBackedTasksManager(File fileName) {
        this.fileName = fileName;
    }
    public static FileBackedTasksManager loadFromFile(File file) throws ManagerSaveException {
        final FileBackedTasksManager fileBackedTasksManager = new FileBackedTasksManager(file);
        fileBackedTasksManager.readFile();
        return fileBackedTasksManager;
    }
    private void readFile() throws ManagerSaveException {
        long taskId = 0;
        try (BufferedReader fileReader = new BufferedReader(new FileReader(fileName, StandardCharsets.UTF_8))) {

            while (fileReader.ready()) {
                String q = fileReader.readLine();
                String firstLine = "id;type;name;status;description;startTime;duration;endTime;epic";
                String w = "Task";
                if (q.isBlank() || firstLine.equals(q)) {//пропуск при чтении первой и пустых строк
                } else if (q.contains(w)) {//заполнение коллекций менеджера задачами из файла
                    Task taskFromFile = fromString(q);
                    Task task = new Task();
                    Subtask subtask = new Subtask();
                    Epic epic = new Epic();
                    if (taskFromFile.getClass() == task.getClass()) {
                        getTaskMap().put(taskFromFile.getTaskId(), taskFromFile);
                        addInDateList(taskFromFile);
                    } else if (taskFromFile.getClass() == epic.getClass()) {
                        epic = (Epic) taskFromFile;
                        getEpicMap().put(taskFromFile.getTaskId(), epic);
                    } else if (taskFromFile.getClass() == subtask.getClass()) {
                        subtask = (Subtask) taskFromFile;
                        for (Epic epicTask1 : getListEpic())
                            if (subtask.getEpicId() == epicTask1.getTaskId()) {
                                epicTask1.setIdSubTasks(subtask.getTaskId());
                            }
                        addInDateList(subtask);
                        getSubTaskMap().put(taskFromFile.getTaskId(), subtask);
                    }
                } else {//заполнение истории просмотра задач
                    List<Long> listId = fromStringList(q);
                    for (Long id : listId) {
                        if (taskId < id) {
                            taskId = id;
                            setId(taskId);//установка максимального значения id из файла
                            // для не дублирования id у задач при создании новых задач
                        }
                    }
                    for (long id : listId) {//заполнение истории просмотров
                        if (getTaskMap().containsKey(id)) {
                            getTaskById(id);
                        } else if (getEpicMap().containsKey(id)) {
                            getEpicById(id);
                        } else if (getSubTaskMap().containsKey(id)) {
                            getSubTaskById(id);
                        }
                    }
                }

            }
        } catch (IOException e) {
            throw new ManagerSaveException("Произошла ошибка во время чтения файла.", e);
        }
    }
    private static List<Long> fromStringList(String value) {//создание списка с id задач для истории просмотра задач из строки
        List<Long> idList = new ArrayList<>();
        String[] split = value.split(",");
        for (String Id : split) {
            try {
                long id = Long.parseLong(Id);
                idList.add(id);
            } catch (NumberFormatException nfe) {
                System.out.println("NumberFormatException: " + nfe.getMessage());
            }

        }
        return idList;
    }

    private static String toString(HistoryManager manager) {
        return manager.toString();
    }

    private static List<Long> fromStringHistory(String value) {
        ArrayList<Long> listTaskHistory = new ArrayList<>();
        String[] split = value.split(",");
        for (String id : split) {
            listTaskHistory.add(Long.parseLong(id));
        }
        return listTaskHistory;
    }

    private Task fromString(String value) { //преобразование строки в задачу
        // параметр в методе записан в формате: idTask;TypeTask;nameTask;TaskStatus;descriptionTask;startTime;duration;endTime;epicId
        Task task;
        String[] split = value.split(";");
        TypeTask typeTask = TypeTask.valueOf(split[1]);
        Long taskId = Long.parseLong(split[0]);
        String taskName = split[2];
        String taskDescription = split[4];
        LocalDateTime startTime;
        LocalDateTime endTime;
        if (split[5].equals("null")) {
            startTime = null;
        } else {
            startTime = LocalDateTime.parse(split[5]);
        }
        if (split[7].equals("null")) {
            endTime = null;
        } else {
            endTime = LocalDateTime.parse(split[7]);
        }
        int duration = Integer.parseInt(split[6]);
        if (typeTask.equals(TypeTask.Task)) {
            task = new Task(taskName, taskDescription, TaskStatus.valueOf(split[3]), taskId, duration, startTime);
        } else if (typeTask.equals(TypeTask.Epic)) {
            task = new Epic(taskName, taskDescription, TaskStatus.valueOf(split[3]), taskId, duration, startTime, endTime);
        } else {
            long epicId = Long.parseLong(split[8]);
            task = new Subtask(taskName, taskDescription, TaskStatus.valueOf(split[3]), taskId, epicId);
        }
        return task;
    }

    public void save() throws ManagerSaveException {//метод записи в файл сохранения
        try (Writer fileWriter = new FileWriter(fileName, StandardCharsets.UTF_8)) {
            fileWriter.write("taskId" + splitter + "type" + splitter + "name" + splitter + "status" + splitter
                    + "description" + splitter
                    + "startTime" + splitter + "duration" + splitter + "endTime" + splitter + "epic\n");
            for (Task task : getAllTasks()) {//перебор всех задач
                String taskToString = task.toString();
                fileWriter.write(taskToString + "\n");//запись в файл
            }
            fileWriter.write(" " + "\n");
            fileWriter.write(toString(getHistoryManager()));//последовательная запись id задач истории в файл
        } catch (IOException e) {
            throw new ManagerSaveException("Произошла ошибка во время записи в файл.", e);
        }
    }


    @Override
    public void addTask(Task task) throws ManagerSaveException { // создание задачи
        super.addTask(task);
        save();
    }

    @Override
    public Task getTaskById(Long id) throws ManagerSaveException {  // получение задачи по id
        final Task result = super.getTaskById(id);
        save();
        return result;
    }


    @Override
    public void updateTask(Task task) throws ManagerSaveException {
        super.updateTask(task);
        save();
    }

    @Override
    public void addEpic(Epic epic) throws ManagerSaveException {
        super.addEpic(epic);
        save();
    }
    @Override
    public void addSubTask(Subtask subtask) throws ManagerSaveException {
        super.addSubTask(subtask);
        save();
    }

    @Override
    public Epic getEpicById(Long id) throws ManagerSaveException {
        final Epic result = super.getEpicById(id);
        save();
        return result;
    }


    @Override
    public Subtask getSubTaskById(Long id) throws ManagerSaveException {
        final Subtask result = super.getSubTaskById(id);
        save();
        return result;
    }

    @Override
    public void updateSubTask(Subtask subtask) throws ManagerSaveException {
        super.updateSubTask(subtask);
        save();
    }

    @Override
    public void deleteTask(Long id) throws ManagerSaveException {
        super.deleteTask(id);
        save();
    }

    @Override
    public void deleteEpic(Long id) throws ManagerSaveException {
        super.deleteEpic(id);
        save();
    }

    @Override
    public void deleteSubTask(Long id) throws ManagerSaveException {
        super.deleteSubTask(id);
        save();
    }
}